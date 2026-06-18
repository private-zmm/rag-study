package com.ragstudy.clipper.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.ragstudy.clipper.controller.dto.ClipperVideoTaskDto;
import com.ragstudy.clipper.controller.dto.ClipperVideoTaskRequest;
import com.ragstudy.clipper.convert.ClipperVideoTaskConvert;
import com.ragstudy.clipper.dal.dataobject.ClipperVideoTaskEntity;
import com.ragstudy.clipper.dal.repository.ClipperVideoTaskRepository;
import com.ragstudy.knowledge.controller.dto.KnowledgeDocumentDto;
import com.ragstudy.knowledge.service.KnowledgeDocumentService;
import com.ragstudy.knowledge.service.KnowledgeIndexService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import jakarta.annotation.PreDestroy;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class ClipperVideoTaskService {

    private static final int DEFAULT_TIMEOUT_SECONDS = 1800;
    private final ExecutorService executorService = Executors.newFixedThreadPool(2);

    private final ClipperVideoTaskRepository taskRepository;
    private final SkillWorkerClient skillWorkerClient;
    private final KnowledgeDocumentService knowledgeDocumentService;
    private final KnowledgeIndexService knowledgeIndexService;

    public ClipperVideoTaskService(
            ClipperVideoTaskRepository taskRepository,
            SkillWorkerClient skillWorkerClient,
            KnowledgeDocumentService knowledgeDocumentService,
            KnowledgeIndexService knowledgeIndexService
    ) {
        this.taskRepository = taskRepository;
        this.skillWorkerClient = skillWorkerClient;
        this.knowledgeDocumentService = knowledgeDocumentService;
        this.knowledgeIndexService = knowledgeIndexService;
    }

    public ClipperVideoTaskDto createTask(String userId, ClipperVideoTaskRequest request) {
        String url = normalizeUrl(request.url());
        String platform = normalizePlatform(request.platform(), url);
        ClipperVideoTaskEntity task = taskRepository.save(new ClipperVideoTaskEntity(
                UUID.randomUUID().toString(),
                userId,
                request.knowledgeBaseId().trim(),
                url,
                platform
        ));
        executorService.submit(() -> runTask(task.getId(), request.language(), request.modelSize(), request.keepTimestamps()));
        return ClipperVideoTaskConvert.toDto(task);
    }

    public ClipperVideoTaskDto getTask(String userId, String taskId) {
        return taskRepository.findByIdAndUserId(taskId, userId)
                .map(ClipperVideoTaskConvert::toDto)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "视频剪藏任务不存在"));
    }

    public void runTask(String taskId, String language, String modelSize, Boolean keepTimestamps) {
        ClipperVideoTaskEntity task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "视频剪藏任务不存在"));
        task.markProcessing();
        taskRepository.save(task);

        try {
            Map<String, Object> input = new LinkedHashMap<>();
            input.put("url", task.getUrl());
            input.put("platform", task.getPlatform());
            input.put("language", StringUtils.hasText(language) ? language.trim() : "zh");
            input.put("keepTimestamps", keepTimestamps == null || keepTimestamps);

            if (StringUtils.hasText(modelSize)) {
                input.put("modelSize", modelSize.trim());
            }

            SkillWorkerClient.SkillRunResponse response = skillWorkerClient.run(
                    "video-transcribe",
                    input,
                    DEFAULT_TIMEOUT_SECONDS
            );

            if (response == null) {
                throw new IllegalStateException("Worker 未返回结果");
            }

            if (!response.success()) {
                throw new IllegalStateException(StringUtils.hasText(response.error()) ? response.error() : "Worker 执行失败");
            }

            JsonNode result = response.result();
            if (result == null) {
                throw new IllegalStateException("Worker 返回结果为空");
            }

            String title = text(result, "title", "视频转写");
            String markdown = text(result, "markdown", "");
            if (!StringUtils.hasText(markdown)) {
                throw new IllegalStateException("Worker 未生成 Markdown 内容");
            }

            KnowledgeDocumentDto document = knowledgeDocumentService.saveVideoDocument(
                    task.getUserId(),
                    task.getKnowledgeBaseId(),
                    task.getUrl(),
                    task.getPlatform(),
                    title,
                    markdown
            );
            knowledgeIndexService.rebuildDocumentIndex(task.getUserId(), task.getKnowledgeBaseId(), document.id());
            task.markCompleted(title, document.id());
            taskRepository.save(task);
        } catch (Exception ex) {
            task.markFailed(ex.getMessage() == null ? "视频分析失败" : ex.getMessage());
            taskRepository.save(task);
        }
    }

    private String normalizeUrl(String url) {
        String trimmedUrl = url == null ? "" : url.trim();
        if (!StringUtils.hasText(trimmedUrl)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请输入视频链接");
        }

        try {
            URI uri = new URI(trimmedUrl);
            String scheme = uri.getScheme();
            if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "仅支持 HTTP/HTTPS 视频链接");
            }
        } catch (URISyntaxException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "视频链接格式不正确");
        }

        return trimmedUrl;
    }

    private String normalizePlatform(String platform, String url) {
        if (StringUtils.hasText(platform) && !"auto".equalsIgnoreCase(platform.trim())) {
            return platform.trim().toLowerCase(Locale.ROOT);
        }

        String loweredUrl = url.toLowerCase(Locale.ROOT);
        if (loweredUrl.contains("douyin.com")) {
            return "douyin";
        }
        if (loweredUrl.contains("bilibili.com") || loweredUrl.contains("b23.tv")) {
            return "bilibili";
        }
        if (loweredUrl.contains("youtube.com") || loweredUrl.contains("youtu.be")) {
            return "youtube";
        }
        return "video";
    }

    private String text(JsonNode node, String fieldName, String fallback) {
        JsonNode value = node.get(fieldName);
        if (value == null || value.isNull()) {
            return fallback;
        }
        String text = value.asText();
        return StringUtils.hasText(text) ? text : fallback;
    }

    @PreDestroy
    public void shutdown() {
        executorService.shutdownNow();
    }
}
