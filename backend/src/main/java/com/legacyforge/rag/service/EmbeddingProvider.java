package com.legacyforge.rag.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Calls an OpenAI-compatible /embeddings endpoint.
 *
 * Configured via env:
 *   - EMBEDDING_BASE_URL  (e.g. http://localhost:11434/v1  or  https://generativelanguage.googleapis.com/v1beta/openai)
 *   - EMBEDDING_API_KEY   (dummy for Ollama, real key for Gemini)
 *   - EMBEDDING_MODEL     (e.g. nomic-embed-text  or  text-embedding-004)
 *
 * Both providers output 768-dim vectors when using the configured models.
 */
@Service
public class EmbeddingProvider {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingProvider.class);

    private final RestClient http;
    private final String model;
    private final String baseUrl;

    public EmbeddingProvider(
            @Value("${app.embedding.base-url:http://localhost:11434/v1}") String baseUrl,
            @Value("${app.embedding.api-key:ollama}") String apiKey,
            @Value("${app.embedding.model:nomic-embed-text}") String model
    ) {
        this.baseUrl = baseUrl;
        this.model = model;
        this.http = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();
        log.info("EmbeddingProvider: base={} model={}", baseUrl, model);
    }

    /** Embed a single string. */
    public float[] embed(String text) {
        return embedBatch(List.of(text)).get(0);
    }

    /** Embed a batch of strings. */
    @SuppressWarnings("unchecked")
    public List<float[]> embedBatch(List<String> texts) {
        if (texts.isEmpty()) return List.of();

        Map<String, Object> body = Map.of(
                "model", model,
                "input", texts
        );
        Map<String, Object> resp;
        try {
            resp = http.post()
                    .uri("/embeddings")
                    .body(body)
                    .retrieve()
                    .body(Map.class);
        } catch (Exception e) {
            log.error("Embedding call failed: {}", e.getMessage());
            throw new RuntimeException("Embedding provider unavailable: " + e.getMessage(), e);
        }
        if (resp == null || !resp.containsKey("data")) {
            throw new RuntimeException("Malformed embedding response");
        }

        List<Map<String, Object>> data = (List<Map<String, Object>>) resp.get("data");
        List<float[]> out = new ArrayList<>(data.size());
        for (Map<String, Object> row : data) {
            Object emb = row.get("embedding");
            if (!(emb instanceof List<?> list)) {
                throw new RuntimeException("embedding is not a list");
            }
            float[] v = new float[list.size()];
            for (int i = 0; i < list.size(); i++) {
                v[i] = ((Number) list.get(i)).floatValue();
            }
            out.add(v);
        }
        return out;
    }

    public String describe() {
        return "%s @ %s".formatted(model, baseUrl);
    }
}
