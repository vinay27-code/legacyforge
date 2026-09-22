package com.legacyforge.analysis.repo;

import com.legacyforge.analysis.entity.FileAnalysis;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface FileAnalysisRepository extends JpaRepository<FileAnalysis, UUID> {

    @Query("SELECT f FROM FileAnalysis f WHERE f.analysisId = :analysisId ORDER BY f.complexity DESC")
    List<FileAnalysis> findTopComplexByAnalysisId(@Param("analysisId") UUID analysisId, Pageable pageable);

    List<FileAnalysis> findByAnalysisIdOrderByFilePathAsc(UUID analysisId);
}
