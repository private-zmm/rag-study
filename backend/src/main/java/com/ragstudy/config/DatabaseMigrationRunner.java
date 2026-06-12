package com.ragstudy.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class DatabaseMigrationRunner implements CommandLineRunner {

    private final DatabaseMigrationService databaseMigrationService;

    public DatabaseMigrationRunner(DatabaseMigrationService databaseMigrationService) {
        this.databaseMigrationService = databaseMigrationService;
    }

    @Override
    public void run(String... args) {
        databaseMigrationService.migrateDatabase();
    }
}
