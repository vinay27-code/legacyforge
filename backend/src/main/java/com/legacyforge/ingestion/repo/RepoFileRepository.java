package com.legacyforge.ingestion.repo;

import com.legacyforge.ingestion.entity.RepoFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RepoFileRepository extends JpaRepository<RepoFile, UUID> {

    /**
     * Return file metadata (no content) so the file tree endpoint stays cheap.
     * Content is loaded separately when a specific file is opened.
     */
    @Query("""
        SELECT new com.legacyforge.ingestion.repo.RepoFileMetadata(
            f.id, f.path, f.sizeBytes, f.language, f.binary
        )
        FROM RepoFile f
        WHERE f.repoId = :repoId
        ORDER BY f.path
        """)
    List<RepoFileMetadata> findMetadataByRepoId(@Param("repoId") UUID repoId);

    Optional<RepoFile> findByRepoIdAndPath(UUID repoId, String path);
}
