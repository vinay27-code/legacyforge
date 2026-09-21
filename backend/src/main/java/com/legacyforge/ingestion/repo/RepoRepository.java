package com.legacyforge.ingestion.repo;

import com.legacyforge.ingestion.entity.Repo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RepoRepository extends JpaRepository<Repo, UUID> {

    List<Repo> findByUserIdOrderByCreatedAtDesc(UUID userId);

    Optional<Repo> findByIdAndUserId(UUID id, UUID userId);
}
