package com.ragstudy.clipper.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class ClipperSchemaMigration implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    public ClipperSchemaMigration(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        jdbcTemplate.execute("ALTER TABLE IF EXISTS web_clips ADD COLUMN IF NOT EXISTS content text");
        jdbcTemplate.execute("ALTER TABLE IF EXISTS web_clips ALTER COLUMN knowledge_base_id DROP NOT NULL");
        jdbcTemplate.execute("ALTER TABLE IF EXISTS web_clips ALTER COLUMN document_id DROP NOT NULL");
    }
}
