package com.ragstudy.note.service;

import com.ragstudy.auth.dal.dataobject.UserEntity;
import com.ragstudy.auth.dal.repository.UserRepository;
import com.ragstudy.auth.controller.dto.AuthRequests.RegisterRequest;
import com.ragstudy.auth.service.AuthService;
import com.ragstudy.note.controller.dto.NoteSaveRequest;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class NoteDataInitializer implements CommandLineRunner {

    private final NoteService noteService;
    private final UserRepository userRepository;
    private final AuthService authService;

    public NoteDataInitializer(NoteService noteService, UserRepository userRepository, AuthService authService) {
        this.noteService = noteService;
        this.userRepository = userRepository;
        this.authService = authService;
    }

    @Override
    public void run(String... args) {
        UserEntity user = userRepository.findByUsernameOrEmail("admin", "admin@local.dev")
                .orElseGet(() -> {
                    authService.register(new RegisterRequest("admin", "admin@local.dev", "admin123", "管理员"));
                    return userRepository.findByUsernameOrEmail("admin", "admin@local.dev").orElseThrow();
                });

        if (!noteService.listNotes(user.getId()).isEmpty()) {
            return;
        }

        noteService.createNote(
                user.getId(),
                new NoteSaveRequest(
                        "项目技术栈",
                        "# 项目技术栈\n\n## 前端\n\n- React\n- TypeScript\n- Vite\n- Ant Design\n- Ant Design X\n\n## 后端\n\n- Java\n- Spring Boot\n- PostgreSQL\n- Qdrant\n"
                )
        );
        noteService.createNote(
                user.getId(),
                new NoteSaveRequest(
                        "Markdown 在线编辑能力",
                        "# Markdown 在线编辑能力\n\n## 首版目标\n\n- 正文编辑\n- 实时预览\n- 文档目录\n- 保存到 PostgreSQL\n\n## 后续能力\n\n- 评论\n- 协同编辑\n- 版本历史\n"
                )
        );
        noteService.createNote(
                user.getId(),
                new NoteSaveRequest(
                        "网页剪藏流程",
                        "# 网页剪藏流程\n\n1. 用户输入 URL\n2. 后端抓取网页\n3. 解析正文\n4. 保存到笔记或知识库\n5. 分块并向量化\n"
                )
        );
    }
}
