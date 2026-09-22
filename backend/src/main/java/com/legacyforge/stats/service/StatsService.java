package com.legacyforge.stats.service;

import com.legacyforge.agents.entity.MigrationArtifact;
import com.legacyforge.agents.repo.MigrationArtifactRepository;
import com.legacyforge.agents.repo.MigrationDependencyRepository;
import com.legacyforge.ingestion.entity.Repo;
import com.legacyforge.ingestion.repo.RepoFileRepository;
import com.legacyforge.ingestion.repo.RepoRepository;
import com.legacyforge.planning.repo.MigrationPlanRepository;
import com.legacyforge.rag.repo.CodeChunkRepository;
import com.legacyforge.stats.dto.PlatformStats;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Rolls up counts across every table we care about into one Dashboard payload.
 * Uses JdbcTemplate for the aggregate queries because JPA's default counting
 * paths would trigger N+1s and full loads on the artifact + dependency tables.
 */
@Service
public class StatsService {

    // Rough token-count heuristic: 4 chars per token. Fine for embedding
    // display where we don't have per-call token counts.
    private static final int CHARS_PER_TOKEN = 4;

    // OpenAI unit prices (USD per 1M tokens) as of Week 11. Adjust when the
    // provider price sheet changes.
    private static final double PRICE_EMBED_PER_M = 0.02;   // text-embedding-3-small
    private static final double PRICE_GPT4O_MINI_IN_PER_M  = 0.15;
    private static final double PRICE_GPT4O_MINI_OUT_PER_M = 0.60;

    private final RepoRepository repos;
    private final RepoFileRepository files;
    private final CodeChunkRepository chunks;
    private final MigrationPlanRepository plans;
    private final MigrationArtifactRepository artifacts;
    private final MigrationDependencyRepository deps;
    private final JdbcTemplate jdbc;

    public StatsService(RepoRepository repos,
                        RepoFileRepository files,
                        CodeChunkRepository chunks,
                        MigrationPlanRepository plans,
                        MigrationArtifactRepository artifacts,
                        MigrationDependencyRepository deps,
                        JdbcTemplate jdbc) {
        this.repos = repos;
        this.files = files;
        this.chunks = chunks;
        this.plans = plans;
        this.artifacts = artifacts;
        this.deps = deps;
        this.jdbc = jdbc;
    }

    public PlatformStats.Response platformStats() {
        long repoCount = repos.count();
        long fileCount = files.count();
        long chunkCount = chunks.count();
        long planCount = plans.count();
        long artifactCount = artifacts.count();
        long depCount = deps.count();
        long brokenCount = queryLong("SELECT COUNT(*) FROM migration_dependencies WHERE NOT resolved", 0L);
        long patchCount = queryLong("SELECT COUNT(*) FROM plan_patches", 0L);

        // Artifact breakdown via one aggregate SQL rather than 6 separate counts.
        Map<String, Object> abd = jdbc.queryForMap(
                "SELECT " +
                        "SUM(CASE WHEN status='SUCCESS' THEN 1 ELSE 0 END) AS success, " +
                        "SUM(CASE WHEN status='FAILED' THEN 1 ELSE 0 END) AS failed, " +
                        "SUM(CASE WHEN validation_status='VALID' THEN 1 ELSE 0 END) AS valid, " +
                        "SUM(CASE WHEN validation_status='INVALID' THEN 1 ELSE 0 END) AS invalid, " +
                        "SUM(CASE WHEN validation_status='SKIPPED' THEN 1 ELSE 0 END) AS skipped, " +
                        "SUM(CASE WHEN parent_artifact_id IS NOT NULL THEN 1 ELSE 0 END) AS derived, " +
                        "COALESCE(SUM(retry_count), 0) AS retries_total " +
                        "FROM migration_artifacts");

        PlatformStats.ArtifactBreakdown breakdown = new PlatformStats.ArtifactBreakdown(
                lng(abd.get("success")),
                lng(abd.get("failed")),
                lng(abd.get("valid")),
                lng(abd.get("invalid")),
                lng(abd.get("skipped")),
                lng(abd.get("derived")),
                lng(abd.get("retries_total"))
        );

        // Token usage: agent + plan tokens from their respective tables.
        long agentIn = queryLong("SELECT COALESCE(SUM(prompt_tokens), 0) FROM migration_artifacts", 0L);
        long agentOut = queryLong("SELECT COALESCE(SUM(output_tokens), 0) FROM migration_artifacts", 0L);
        long planIn = queryLong("SELECT COALESCE(SUM(prompt_tokens), 0) FROM migration_plans", 0L);
        long planOut = queryLong("SELECT COALESCE(SUM(output_tokens), 0) FROM migration_plans", 0L);

        // Embedding tokens: rough heuristic from chunk content length.
        long embedChars = queryLong("SELECT COALESCE(SUM(LENGTH(content)), 0) FROM code_chunks", 0L);
        long embedTokens = embedChars / CHARS_PER_TOKEN;

        double cost =
                (embedTokens / 1_000_000.0) * PRICE_EMBED_PER_M
              + (agentIn + planIn) / 1_000_000.0 * PRICE_GPT4O_MINI_IN_PER_M
              + (agentOut + planOut) / 1_000_000.0 * PRICE_GPT4O_MINI_OUT_PER_M;

        PlatformStats.TokenUsage tokens = new PlatformStats.TokenUsage(
                embedTokens, planIn, planOut, agentIn, agentOut,
                embedTokens + planIn + planOut + agentIn + agentOut,
                round2(cost)
        );

        List<PlatformStats.RecentActivity> recent = recentActivity();

        return new PlatformStats.Response(
                new PlatformStats.Counts(repoCount, fileCount, chunkCount, planCount,
                        artifactCount, depCount, brokenCount, patchCount),
                breakdown, tokens, recent
        );
    }

    private List<PlatformStats.RecentActivity> recentActivity() {
        List<PlatformStats.RecentActivity> out = new ArrayList<>();

        // Plans
        List<Map<String, Object>> planRows = jdbc.queryForList(
                "SELECT p.repo_id, r.name, p.generated_at " +
                        "FROM migration_plans p JOIN repos r ON r.id = p.repo_id " +
                        "ORDER BY p.generated_at DESC LIMIT 5");
        for (Map<String, Object> row : planRows) {
            out.add(new PlatformStats.RecentActivity("PLAN",
                    (String) row.get("name"),
                    toInstant(row.get("generated_at")),
                    "Migration plan generated"));
        }

        // Agent runs: latest completed artifact per repo
        List<Map<String, Object>> agentRows = jdbc.queryForList(
                "SELECT r.name, MAX(a.completed_at) AS last_completed, COUNT(*) AS n " +
                        "FROM migration_artifacts a JOIN repos r ON r.id = a.repo_id " +
                        "WHERE a.completed_at IS NOT NULL " +
                        "GROUP BY r.name ORDER BY last_completed DESC LIMIT 5");
        for (Map<String, Object> row : agentRows) {
            out.add(new PlatformStats.RecentActivity("AGENT_RUN",
                    (String) row.get("name"),
                    toInstant(row.get("last_completed")),
                    row.get("n") + " artifacts generated"));
        }

        // Ingest events: repo created_at
        List<Map<String, Object>> repoRows = jdbc.queryForList(
                "SELECT name, created_at, file_count FROM repos " +
                        "ORDER BY created_at DESC LIMIT 5");
        for (Map<String, Object> row : repoRows) {
            out.add(new PlatformStats.RecentActivity("INDEX",
                    (String) row.get("name"),
                    toInstant(row.get("created_at")),
                    row.get("file_count") + " files ingested"));
        }

        out.sort(Comparator.comparing(PlatformStats.RecentActivity::when).reversed());
        return out.size() > 10 ? out.subList(0, 10) : out;
    }

    // ---- helpers -------------------------------------------------------

    private long queryLong(String sql, long fallback) {
        try {
            Long n = jdbc.queryForObject(sql, Long.class);
            return n == null ? fallback : n;
        } catch (Exception e) {
            return fallback;
        }
    }

    private static long lng(Object o) {
        if (o == null) return 0L;
        if (o instanceof Number n) return n.longValue();
        try { return Long.parseLong(o.toString()); } catch (Exception e) { return 0L; }
    }

    private static Instant toInstant(Object o) {
        if (o instanceof Instant i) return i;
        if (o instanceof java.sql.Timestamp t) return t.toInstant();
        if (o instanceof java.time.OffsetDateTime odt) return odt.toInstant();
        if (o instanceof java.time.LocalDateTime ldt) return ldt.atOffset(java.time.ZoneOffset.UTC).toInstant();
        return Instant.EPOCH;
    }

    private static double round2(double d) {
        return Math.round(d * 100.0) / 100.0;
    }
}
