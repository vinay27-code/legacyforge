package com.legacyforge.rag.service;

import com.legacyforge.rag.dto.SearchHit;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class SearchService {

    private final EmbeddingProvider embedder;
    private final JdbcTemplate jdbc;

    public SearchService(EmbeddingProvider embedder, JdbcTemplate jdbc) {
        this.embedder = embedder;
        this.jdbc = jdbc;
    }

    /**
     * Semantic search inside one repo. Embeds the query, then runs
     * a cosine-similarity nearest-neighbour query against code_chunks.
     */
    public List<SearchHit> search(UUID repoId, String query, int k) {
        float[] q = embedder.embed(query);
        String vec = IndexingService.toVectorLiteral(q);

        String sql = """
                SELECT id, file_id, file_path, chunk_type, class_name, method_name,
                       start_line, end_line, content,
                       1 - (embedding <=> ?::vector) AS similarity
                FROM code_chunks
                WHERE repo_id = ?
                ORDER BY embedding <=> ?::vector
                LIMIT ?
                """;

        RowMapper<SearchHit> mapper = (rs, i) -> new SearchHit(
                (UUID) rs.getObject("id"),
                (UUID) rs.getObject("file_id"),
                rs.getString("file_path"),
                rs.getString("chunk_type"),
                rs.getString("class_name"),
                rs.getString("method_name"),
                (Integer) rs.getObject("start_line"),
                (Integer) rs.getObject("end_line"),
                rs.getString("content"),
                rs.getDouble("similarity")
        );

        return jdbc.query(sql, mapper, vec, repoId, vec, Math.max(1, Math.min(50, k)));
    }
}
