package com.legacyforge.planning.repo;

import com.legacyforge.planning.entity.PlanPatch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PlanPatchRepository extends JpaRepository<PlanPatch, UUID> {

    List<PlanPatch> findByRepoIdOrderByCreatedAtDesc(UUID repoId);
}
