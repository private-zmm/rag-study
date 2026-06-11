package com.ragstudy.note.dal.repository;

import com.ragstudy.note.dal.dataobject.NoteAssetEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NoteAssetRepository extends JpaRepository<NoteAssetEntity, String> {

    List<NoteAssetEntity> findAllByUserIdAndNoteId(String userId, String noteId);

    List<NoteAssetEntity> findAllByUserIdAndObjectNameIn(String userId, List<String> objectNames);
}
