package com.legacyforge.analysis.repo;

import com.legacyforge.analysis.entity.AnalysisReport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AnalysisReportRepository extends JpaRepository<AnalysisReport, UUID> {
    Optional<AnalysisReport> findByRepoId(UUID repoId);
    void deleteByRepoId(UUID repoId);
}
