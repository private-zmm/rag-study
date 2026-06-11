package com.ragstudy.note.dal.dataobject;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "note_assets")
public class NoteAssetEntity {

    @Id
    private String id;

    @Column(nullable = false)
    private String userId;

    @Column
    private String noteId;

    @Column(nullable = false, unique = true, length = 1024)
    private String objectName;

    @Column(nullable = false)
    private String originalName;

    @Column(nullable = false)
    private String contentType;

    @Column(nullable = false)
    private long size;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    protected NoteAssetEntity() {
    }

    public NoteAssetEntity(
            String id,
            String userId,
            String noteId,
            String objectName,
            String originalName,
            String contentType,
            long size,
            LocalDateTime createdAt
    ) {
        this.id = id;
        this.userId = userId;
        this.noteId = noteId;
        this.objectName = objectName;
        this.originalName = originalName;
        this.contentType = contentType;
        this.size = size;
        this.createdAt = createdAt;
    }

    public String getId() {
        return id;
    }

    public String getUserId() {
        return userId;
    }

    public String getNoteId() {
        return noteId;
    }

    public String getObjectName() {
        return objectName;
    }

    public String getOriginalName() {
        return originalName;
    }

    public String getContentType() {
        return contentType;
    }

    public long getSize() {
        return size;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void bindToNote(String noteId) {
        this.noteId = noteId;
    }

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
