package com.legacyforge.planning.repo;

import com.legacyforge.planning.entity.MigrationPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface MigrationPlanRepository extends JpaRepository<MigrationPlan, UUID> {

    Optional<MigrationPlan> findByRepoId(UUID repoId);

    @Modifying
    @Query("DELETE FROM MigrationPlan m WHERE m.repoId = :repoId")
    void deleteByRepoId(UUID repoId);
}
