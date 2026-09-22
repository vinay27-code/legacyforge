package com.legacyforge.rag.service;

import com.legacyforge.ingestion.entity.Repo;
import com.legacyforge.ingestion.entity.RepoFile;
import com.legacyforge.ingestion.repo.RepoFileRepository;
import com.legacyforge.rag.entity.CodeChunk;
import com.legacyforge.rag.repo.CodeChunkRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class IndexingService {

    private static final Logger log = LoggerFactory.getLogger(IndexingService.class);
    private static final int BATCH_SIZE = 32;

    private final ChunkingService chunker;
    private final EmbeddingProvider embedder;
    private final CodeChunkRepository chunks;
    private final RepoFileRepository files;
    private final JdbcTemplate jdbc;

    public IndexingService(ChunkingService chunker, EmbeddingProvider embedder,
                           CodeChunkRepository chunks, RepoFileRepository files,
                           JdbcTemplate jdbc) {
        this.chunker = chunker;
        this.embedder = embedder;
        this.chunks = chunks;
        this.files = files;
        this.jdbc = jdbc;
    }

    /**
     * Rebuild the entire chunk + embedding index for a repo.
     * Deletes existing chunks first, then chunks each file, batches embedding
     * calls, and inserts via JdbcTemplate so we can cast the vector literal.
     */
    @Transactional
    public IndexResult reindex(Repo repo) {
        chunks.deleteByRepoId(repo.getId());

        List<RepoFile> allFiles = files.findAll().stream()
                .filter(f -> f.getRepoId().equals(repo.getId()))
                .toList();

        List<CodeChunk> allChunks = new ArrayList<>();
        for (RepoFile f : allFiles) {
            allChunks.addAll(chunker.chunk(f));
        }

        if (allChunks.isEmpty()) {
            log.info("No chunks produced for repo {}", repo.getId());
            return new IndexResult(0, 0);
        }

        int totalEmbedded = 0;
        for (int i = 0; i < allChunks.size(); i += BATCH_SIZE) {
            int end = Math.min(allChunks.size(), i + BATCH_SIZE);
            List<CodeChunk> batch = allChunks.subList(i, end);
            List<String> texts = batch.stream().map(CodeChunk::getContent).toList();

            List<float[]> vectors = embedder.embedBatch(texts);
            if (vectors.size() != batch.size()) {
                throw new IllegalStateException("Embedding count mismatch: expected " + batch.size() + ", got " + vectors.size());
            }
            for (int j = 0; j < batch.size(); j++) {
                insertChunk(batch.get(j), vectors.get(j));
            }
            totalEmbedded += batch.size();
            log.debug("Indexed {} / {} chunks", totalEmbedded, allChunks.size());

            // Throttle: Gemini free tier caps embed calls at 100 RPM.
            if (i + BATCH_SIZE < allChunks.size()) {
                try {
                    Thread.sleep(700);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupted while throttling embed calls", e);
                }
            }
        }

        log.info("Reindex complete for repo {}: {} chunks embedded", repo.getId(), totalEmbedded);
        return new IndexResult(totalEmbedded, allFiles.size());
    }

    private void insertChunk(CodeChunk c, float[] embedding) {
        UUID id = UUID.randomUUID();
        String vectorLiteral = toVectorLiteral(embedding);
        jdbc.update(
                "INSERT INTO code_chunks (id, repo_id, file_id, file_path, chunk_type, chunk_index, "
                        + "class_name, method_name, start_line, end_line, content, embedding) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::vector)",
                id, c.getRepoId(), c.getFileId(), c.getFilePath(),
                c.getChunkType().name(), c.getChunkIndex(), c.getClassName(), c.getMethodName(),
                c.getStartLine(), c.getEndLine(), c.getContent(), vectorLiteral
        );
    }

    /** Serialise a float[] to Postgres pgvector literal: "[1.0,2.0,3.0]". */
    public static String toVectorLiteral(float[] v) {
        StringBuilder sb = new StringBuilder(v.length * 8 + 2);
        sb.append('[');
        for (int i = 0; i < v.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(v[i]);
        }
        sb.append(']');
        return sb.toString();
    }

    public record IndexResult(int chunkCount, int fileCount) {}
}
