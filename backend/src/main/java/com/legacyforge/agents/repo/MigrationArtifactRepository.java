package com.legacyforge.agents.repo;

import com.legacyforge.agents.entity.MigrationArtifact;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface MigrationArtifactRepository extends JpaRepository<MigrationArtifact, UUID> {

    List<MigrationArtifact> findByRepoIdOrderByPhaseNumberAscFilePathAsc(UUID repoId);

    long countByRepoIdAndStatus(UUID repoId, MigrationArtifact.Status status);

    @Modifying
    @Query("DELETE FROM MigrationArtifact a WHERE a.repoId = :repoId")
    void deleteByRepoId(UUID repoId);
}
