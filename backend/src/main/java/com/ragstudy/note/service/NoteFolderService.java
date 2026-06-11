package com.ragstudy.note.service;

import com.ragstudy.note.controller.dto.NoteFolderDto;
import com.ragstudy.note.controller.dto.NoteFolderRequest;
import com.ragstudy.note.convert.NoteFolderConvert;
import com.ragstudy.note.dal.dataobject.NoteFolderEntity;
import com.ragstudy.note.dal.repository.NoteFolderRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class NoteFolderService {

    private final NoteFolderRepository noteFolderRepository;

    public NoteFolderService(NoteFolderRepository noteFolderRepository) {
        this.noteFolderRepository = noteFolderRepository;
    }

    @Transactional(readOnly = true)
    public List<NoteFolderDto> listFolders(String userId) {
        return noteFolderRepository.findAllByUserIdOrderByPathAsc(userId)
                .stream()
                .map(NoteFolderConvert::toDto)
                .toList();
    }

    @Transactional
    public NoteFolderDto createFolder(String userId, NoteFolderRequest request) {
        String path = normalizePath(request.path());

        if (!StringUtils.hasText(path)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "文件夹名称不能为空");
        }

        if (noteFolderRepository.existsByUserIdAndPath(userId, path)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "文件夹已存在");
        }

        LocalDateTime now = LocalDateTime.now();
        NoteFolderEntity folder = new NoteFolderEntity(UUID.randomUUID().toString(), userId, path, now, now);

        return NoteFolderConvert.toDto(noteFolderRepository.save(folder));
    }

    @Transactional
    public void deleteFolder(String userId, String id) {
        NoteFolderEntity folder = noteFolderRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "文件夹不存在"));

        noteFolderRepository.delete(folder);
    }

    private String normalizePath(String path) {
        return path
                .replace("\\", "/")
                .replaceAll("/+", "/")
                .replaceAll("^/|/$", "")
                .trim();
    }
}
