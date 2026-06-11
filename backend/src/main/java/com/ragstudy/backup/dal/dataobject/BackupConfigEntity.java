package com.ragstudy.backup.dal.dataobject;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "backup_configs")
public class BackupConfigEntity {

    @Id
    private String id;

    @Column(nullable = false)
    private boolean enabled;

    @Column(nullable = false)
    private String endpoint;

    @Column(nullable = false)
    private String bucket;

    @Column(nullable = false)
    private String accessKey;

    @Column(nullable = false)
    private String secretKey;

    private String region;

    @Column(nullable = false)
    private String prefix;

    @Column(nullable = false)
    private String schedule;

    @Column(nullable = false)
    private Integer retentionCount;

    @Column(nullable = false)
    private Integer retentionDays;

    @Column(nullable = false)
    private boolean pathStyleAccess;

    @Column(nullable = false)
    private String pgDumpPath;

    @Column(nullable = false)
    private String psqlPath;

    private Instant lastBackupAt;

    private Instant createdAt;

    private Instant updatedAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getBucket() {
        return bucket;
    }

    public void setBucket(String bucket) {
        this.bucket = bucket;
    }

    public String getAccessKey() {
        return accessKey;
    }

    public void setAccessKey(String accessKey) {
        this.accessKey = accessKey;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    public String getSchedule() {
        return schedule;
    }

    public void setSchedule(String schedule) {
        this.schedule = schedule;
    }

    public Integer getRetentionCount() {
        return retentionCount;
    }

    public void setRetentionCount(Integer retentionCount) {
        this.retentionCount = retentionCount;
    }

    public Integer getRetentionDays() {
        return retentionDays;
    }

    public void setRetentionDays(Integer retentionDays) {
        this.retentionDays = retentionDays;
    }

    public boolean isPathStyleAccess() {
        return pathStyleAccess;
    }

    public void setPathStyleAccess(boolean pathStyleAccess) {
        this.pathStyleAccess = pathStyleAccess;
    }

    public String getPgDumpPath() {
        return pgDumpPath;
    }

    public void setPgDumpPath(String pgDumpPath) {
        this.pgDumpPath = pgDumpPath;
    }

    public String getPsqlPath() {
        return psqlPath;
    }

    public void setPsqlPath(String psqlPath) {
        this.psqlPath = psqlPath;
    }

    public Instant getLastBackupAt() {
        return lastBackupAt;
    }

    public void setLastBackupAt(Instant lastBackupAt) {
        this.lastBackupAt = lastBackupAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
