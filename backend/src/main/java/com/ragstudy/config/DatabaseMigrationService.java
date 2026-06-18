package com.ragstudy.config;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class DatabaseMigrationService {

    private final JdbcTemplate jdbcTemplate;

    public DatabaseMigrationService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void migrateDatabase() {
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
        jdbcTemplate.execute("alter table if exists knowledge_documents add column if not exists content_hash varchar(64)");
        jdbcTemplate.execute("""
                create table if not exists note_sync_tasks (
                    id varchar(255) primary key,
                    user_id varchar(255) not null,
                    knowledge_base_id varchar(255) not null,
                    status varchar(32) not null,
                    total_notes integer not null,
                    processed_notes integer not null,
                    synced_notes integer not null,
                    skipped_notes integer not null,
                    indexed_chunks integer not null,
                    embedding_model varchar(255),
                    error_message text,
                    created_at timestamp not null,
                    updated_at timestamp not null,
                    started_at timestamp,
                    finished_at timestamp
                )
                """);
        jdbcTemplate.execute("create index if not exists idx_note_sync_tasks_user_updated on note_sync_tasks(user_id, updated_at desc)");
        jdbcTemplate.execute("create index if not exists idx_note_sync_tasks_user_status on note_sync_tasks(user_id, status)");
        jdbcTemplate.execute("""
                create table if not exists clipper_proxy_configs (
                    id varchar(255) primary key,
                    protocol varchar(32) not null,
                    host varchar(255) not null,
                    port integer not null,
                    username varchar(255),
                    password varchar(255),
                    created_at timestamp,
                    updated_at timestamp
                )
                """);
        jdbcTemplate.execute("""
                create table if not exists clipper_video_tasks (
                    id varchar(255) primary key,
                    user_id varchar(255) not null,
                    knowledge_base_id varchar(255) not null,
                    url text not null,
                    platform varchar(64) not null,
                    status varchar(32) not null,
                    title varchar(255),
                    document_id varchar(255),
                    error_message text,
                    created_at timestamp not null,
                    updated_at timestamp not null,
                    started_at timestamp,
                    finished_at timestamp
                )
                """);
        jdbcTemplate.execute("create index if not exists idx_clipper_video_tasks_user_updated on clipper_video_tasks(user_id, updated_at desc)");
        jdbcTemplate.execute("create index if not exists idx_clipper_video_tasks_user_status on clipper_video_tasks(user_id, status)");
    }
}
