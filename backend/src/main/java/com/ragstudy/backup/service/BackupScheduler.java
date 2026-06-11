package com.ragstudy.backup.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class BackupScheduler {

    private final BackupService backupService;

    public BackupScheduler(BackupService backupService) {
        this.backupService = backupService;
    }

    @Scheduled(cron = "0 0 * * * *")
    public void runScheduledBackup() {
        Instant now = Instant.now();

        if (backupService.shouldRunScheduledBackup(now)) {
            backupService.createBackup("scheduled");
        }
    }
}
