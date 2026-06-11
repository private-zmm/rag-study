package com.ragstudy.mock;

import com.ragstudy.clipper.controller.dto.ClipperRequest;
import com.ragstudy.clipper.controller.dto.ClipperResultDto;
import com.ragstudy.knowledge.controller.dto.KnowledgeBaseDto;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class MockDataService {

    public List<KnowledgeBaseDto> listKnowledgeBases() {
        return List.of(
                new KnowledgeBaseDto("kb-1", "默认知识库", "自动收集笔记、网页剪藏和上传资料。", 18, 426, "ready"),
                new KnowledgeBaseDto("kb-2", "项目笔记", "当前项目规划、学习笔记和技术选型。", 7, 132, "indexing"),
                new KnowledgeBaseDto("kb-3", "网页剪藏", "通过应用内 URL 抓取保存的网页资料。", 0, 0, "empty")
        );
    }

    public ClipperResultDto clip(ClipperRequest request) {
        return new ClipperResultDto(
                UUID.randomUUID().toString(),
                request.url(),
                "网页剪藏任务",
                request.target() == null ? "knowledge" : request.target(),
                request.mode() == null ? "auto" : request.mode(),
                "queued"
        );
    }
}
