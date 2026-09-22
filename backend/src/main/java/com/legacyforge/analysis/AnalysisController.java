package com.legacyforge.analysis;

import com.legacyforge.analysis.dto.AnalysisReportDto;
import com.legacyforge.analysis.dto.FileAnalysisDto;
import com.legacyforge.analysis.entity.AnalysisReport;
import com.legacyforge.analysis.repo.AnalysisReportRepository;
import com.legacyforge.analysis.repo.FileAnalysisRepository;
import com.legacyforge.analysis.service.AnalysisService;
import com.legacyforge.common.ApiException;
import com.legacyforge.ingestion.entity.Repo;
import com.legacyforge.ingestion.repo.RepoRepository;
import com.legacyforge.security.UserPrincipal;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/repos/{repoId}/analysis")
public class AnalysisController {

    private final AnalysisService analysis;
    private final AnalysisReportRepository reports;
    private final FileAnalysisRepository fileReports;
    private final RepoRepository repos;

    public AnalysisController(AnalysisService analysis, AnalysisReportRepository reports,
                              FileAnalysisRepository fileReports, RepoRepository repos) {
        this.analysis = analysis;
        this.reports = reports;
        this.fileReports = fileReports;
        this.repos = repos;
    }

    @PostMapping
    public ResponseEntity<AnalysisReportDto> run(
            @AuthenticationPrincipal UserPrincipal me,
            @PathVariable UUID repoId
    ) {
        Repo repo = loadOwned(me.getId(), repoId);
        AnalysisReport report = analysis.analyze(repo);
        return ResponseEntity.status(HttpStatus.CREATED).body(AnalysisReportDto.from(report));
    }

    @GetMapping
    public ResponseEntity<AnalysisReportDto> get(
            @AuthenticationPrincipal UserPrincipal me,
            @PathVariable UUID repoId
    ) {
        Repo repo = loadOwned(me.getId(), repoId);
        return reports.findByRepoId(repo.getId())
                .map(r -> ResponseEntity.ok(AnalysisReportDto.from(r)))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/top-complex")
    public List<FileAnalysisDto> topComplex(
            @AuthenticationPrincipal UserPrincipal me,
            @PathVariable UUID repoId,
            @RequestParam(defaultValue = "20") int limit
    ) {
        Repo repo = loadOwned(me.getId(), repoId);
        AnalysisReport report = reports.findByRepoId(repo.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Analysis not run yet"));
        int cap = Math.min(200, Math.max(1, limit));
        return fileReports.findTopComplexByAnalysisId(report.getId(), PageRequest.of(0, cap))
                .stream().map(FileAnalysisDto::from).toList();
    }

    private Repo loadOwned(UUID userId, UUID repoId) {
        return repos.findByIdAndUserId(repoId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Repo not found"));
    }
}
