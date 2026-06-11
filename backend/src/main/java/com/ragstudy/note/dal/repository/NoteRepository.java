package com.ragstudy.note.dal.repository;

import com.ragstudy.note.dal.dataobject.NoteEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface NoteRepository extends JpaRepository<NoteEntity, String> {

    List<NoteEntity> findAllByUserIdOrderByUpdatedAtDesc(String userId);

    @Query("""
            select note from NoteEntity note
            where note.userId = :userId
              and (
                lower(note.title) like lower(concat('%', :query, '%'))
                or lower(note.content) like lower(concat('%', :query, '%'))
              )
            order by note.updatedAt desc
            """)
    List<NoteEntity> searchByUserId(@Param("userId") String userId, @Param("query") String query, Pageable pageable);

    long countByUserId(String userId);
}
