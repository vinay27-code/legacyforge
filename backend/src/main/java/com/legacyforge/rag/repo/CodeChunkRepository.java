package com.legacyforge.rag.repo;

import com.legacyforge.rag.entity.CodeChunk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface CodeChunkRepository extends JpaRepository<CodeChunk, UUID> {

    @Modifying
    @Query("DELETE FROM CodeChunk c WHERE c.repoId = :repoId")
    int deleteByRepoId(@Param("repoId") UUID repoId);

    long countByRepoId(UUID repoId);
}
