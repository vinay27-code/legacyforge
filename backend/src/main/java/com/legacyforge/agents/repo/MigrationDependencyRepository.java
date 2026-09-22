package com.legacyforge.agents.repo;

import com.legacyforge.agents.entity.MigrationDependency;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface MigrationDependencyRepository extends JpaRepository<MigrationDependency, UUID> {

    List<MigrationDependency> findByRepoId(UUID repoId);

    List<MigrationDependency> findByFromArtifactId(UUID fromArtifactId);

    long countByRepoIdAndResolvedFalse(UUID repoId);

    @Modifying
    @Query("DELETE FROM MigrationDependency d WHERE d.repoId = :repoId")
    void deleteByRepoId(UUID repoId);
}
