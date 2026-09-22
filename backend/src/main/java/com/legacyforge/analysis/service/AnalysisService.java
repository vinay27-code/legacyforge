package com.legacyforge.analysis.service;

import com.legacyforge.analysis.entity.AnalysisReport;
import com.legacyforge.analysis.entity.FileAnalysis;
import com.legacyforge.analysis.repo.AnalysisReportRepository;
import com.legacyforge.analysis.repo.FileAnalysisRepository;
import com.legacyforge.ingestion.entity.Repo;
import com.legacyforge.ingestion.entity.RepoFile;
import com.legacyforge.ingestion.repo.RepoFileRepository;
import com.legacyforge.ingestion.repo.RepoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Service
public class AnalysisService {

    private static final Logger log = LoggerFactory.getLogger(AnalysisService.class);

    private final RepoRepository repos;
    private final RepoFileRepository files;
    private final AnalysisReportRepository reports;
    private final FileAnalysisRepository fileReports;
    private final JavaAstAnalyzer astAnalyzer;
    private final FrameworkFingerprinter fingerprinter;

    public AnalysisService(RepoRepository repos, RepoFileRepository files,
                           AnalysisReportRepository reports, FileAnalysisRepository fileReports,
                           JavaAstAnalyzer astAnalyzer, FrameworkFingerprinter fingerprinter) {
        this.repos = repos;
        this.files = files;
        this.reports = reports;
        this.fileReports = fileReports;
        this.astAnalyzer = astAnalyzer;
        this.fingerprinter = fingerprinter;
    }

    @Transactional
    public AnalysisReport analyze(Repo repo) {
        reports.deleteByRepoId(repo.getId());
        AnalysisReport report = new AnalysisReport(repo.getId());
        report.setStatus(AnalysisReport.Status.PENDING);
        report = reports.save(report);

        List<RepoFile> allFiles = files.findAll().stream()
                .filter(f -> f.getRepoId().equals(repo.getId()))
                .toList();

        List<FileAnalysis> perFile = new ArrayList<>();
        List<String> allImports = new ArrayList<>();
        long totalLoc = 0L;
        int totalJava = 0;
        int maxComplexity = 0;
        long totalComplexity = 0L;

        for (RepoFile f : allFiles) {
            if (!"java".equals(f.getLanguage()) || f.isBinary() || f.getContent() == null) continue;
            totalJava++;

            JavaAstAnalyzer.Result r = astAnalyzer.analyze(f.getPath(), f.getContent());
            allImports.addAll(r.imports);
            totalLoc += r.loc;
            totalComplexity += r.complexity;
            if (r.complexity > maxComplexity) maxComplexity = r.complexity;

            FileAnalysis fa = new FileAnalysis();
            fa.setAnalysisId(report.getId());
            fa.setFileId(f.getId());
            fa.setFilePath(f.getPath());
            fa.setClassName(r.className);
            fa.setPackageName(r.packageName);
            fa.setLoc(r.loc);
            fa.setMethodCount(r.methodCount);
            fa.setComplexity(r.complexity);
            fa.setImports(r.imports);
            fa.setFrameworks(new ArrayList<>(r.frameworks));
            fa.setFindings(r.findings);
            perFile.add(fa);
        }
        fileReports.saveAll(perFile);

        List<FrameworkFingerprinter.Detected> frameworks = fingerprinter.fingerprint(allFiles, allImports);

        // Repo-level findings: roll up per-file findings by code, top 20
        Map<String, Integer> findingCounts = new LinkedHashMap<>();
        Map<String, String> findingSeverity = new HashMap<>();
        Map<String, String> findingMsg = new HashMap<>();
        for (FileAnalysis fa : perFile) {
            for (Map<String, Object> f : fa.getFindings()) {
                String code = String.valueOf(f.get("code"));
                findingCounts.merge(code, 1, Integer::sum);
                findingSeverity.putIfAbsent(code, String.valueOf(f.get("severity")));
                findingMsg.putIfAbsent(code, String.valueOf(f.get("message")));
            }
        }
        List<Map<String, Object>> repoFindings = findingCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(20)
                .map(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("code", e.getKey());
                    m.put("severity", findingSeverity.get(e.getKey()));
                    m.put("message", findingMsg.get(e.getKey()));
                    m.put("occurrences", e.getValue());
                    return m;
                })
                .toList();

        // Language + LOC breakdown across the whole repo
        Map<String, LangStat> byLang = new LinkedHashMap<>();
        for (RepoFile f : allFiles) {
            if (f.getLanguage() == null) continue;
            LangStat s = byLang.computeIfAbsent(f.getLanguage(), k -> new LangStat());
            s.files++;
            s.bytes += f.getSizeBytes();
        }
        List<Map<String, Object>> langBreakdown = byLang.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue().bytes, a.getValue().bytes))
                .map(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("language", e.getKey());
                    m.put("files", e.getValue().files);
                    m.put("bytes", e.getValue().bytes);
                    return m;
                })
                .toList();

        LinkedHashMap<String, Object> summary = new LinkedHashMap<>();
        summary.put("languageBreakdown", langBreakdown);
        summary.put("javaFileCount", totalJava);
        summary.put("javaLoc", totalLoc);
        summary.put("methodCount", perFile.stream().mapToInt(FileAnalysis::getMethodCount).sum());

        List<Map<String, Object>> frameworksJson = frameworks.stream().map(fw -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("key", fw.key());
            m.put("name", fw.name());
            m.put("version", fw.version());
            m.put("confidence", fw.confidence());
            m.put("evidence", fw.evidence());
            return m;
        }).toList();

        report.setFrameworks(frameworksJson);
        report.setSummary(summary);
        report.setFindings(repoFindings);
        report.setTotalJavaFiles(totalJava);
        report.setTotalJavaLoc(totalLoc);
        report.setMaxComplexity(maxComplexity);
        report.setAvgComplexity(totalJava == 0
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(totalComplexity).divide(BigDecimal.valueOf(totalJava), 2, RoundingMode.HALF_UP));
        report.setStatus(AnalysisReport.Status.READY);
        report = reports.save(report);

        log.info("Analysis complete for repo {}: {} java files, {} LOC, {} frameworks",
                repo.getId(), totalJava, totalLoc, frameworks.size());
        return report;
    }

    private static class LangStat { int files; long bytes; }
}
