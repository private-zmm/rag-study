package com.ragstudy.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class DatabaseMigrationRunner implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;

    public DatabaseMigrationRunner(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) {
        jdbcTemplate.execute("alter table if exists notes add column if not exists user_id varchar(255)");
        jdbcTemplate.execute("alter table if exists users add column if not exists nickname varchar(255)");
        jdbcTemplate.execute("alter table if exists users add column if not exists created_at timestamp");
        jdbcTemplate.execute("alter table if exists users add column if not exists updated_at timestamp");
        jdbcTemplate.execute("""
                create table if not exists web_clips (
                    id varchar(255) primary key,
                    user_id varchar(255) not null,
                    knowledge_base_id varchar(255) not null,
                    document_id varchar(255) not null,
                    url text not null,
                    canonical_url text not null,
                    title varchar(255) not null,
                    site_name varchar(255),
                    excerpt text not null,
                    content_hash varchar(255),
                    status varchar(255) not null,
                    created_at timestamp not null,
                    updated_at timestamp not null
                )
                """);
        jdbcTemplate.execute("create index if not exists idx_web_clips_user_updated on web_clips(user_id, updated_at desc)");
        jdbcTemplate.execute("create index if not exists idx_web_clips_user_canonical_url on web_clips(user_id, canonical_url)");
        jdbcTemplate.execute("alter table if exists backup_configs add column if not exists retention_days integer not null default 14");
        jdbcTemplate.execute("alter table if exists backup_configs add column if not exists path_style_access boolean not null default true");
        jdbcTemplate.execute("alter table if exists backup_configs add column if not exists pg_dump_path varchar(1024) not null default 'pg_dump'");
        jdbcTemplate.execute("alter table if exists backup_configs add column if not exists psql_path varchar(1024) not null default 'psql'");
    }
}
