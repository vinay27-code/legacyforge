package com.legacyforge.planning.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Calls an OpenAI-compatible /chat/completions endpoint. Used both by the
 * planning service (JSON mode) and the migration agents (plain text output).
 *
 * Configured via env:
 *   - PLANNING_BASE_URL  (e.g. http://localhost:11434/v1  or  https://api.openai.com/v1)
 *   - PLANNING_API_KEY   (dummy for Ollama, real key for OpenAI/Gemini)
 *   - PLANNING_MODEL     (e.g. llama3.2  or  gpt-4o-mini)
 */
@Service
public class LlmProvider {

    private static final Logger log = LoggerFactory.getLogger(LlmProvider.class);

    private final RestClient http;
    private final String model;
    private final String baseUrl;

    public LlmProvider(
            @Value("${app.planning.base-url:http://localhost:11434/v1}") String baseUrl,
            @Value("${app.planning.api-key:ollama}") String apiKey,
            @Value("${app.planning.model:llama3.2}") String model
    ) {
        this.baseUrl = baseUrl;
        this.model = model;

        SimpleClientHttpRequestFactory rf = new SimpleClientHttpRequestFactory();
        rf.setConnectTimeout((int) Duration.ofSeconds(15).toMillis());
        rf.setReadTimeout((int) Duration.ofSeconds(180).toMillis());

        this.http = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(rf)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();
        log.info("LlmProvider: base={} model={}", baseUrl, model);
    }

    /** JSON-mode completion. Used by the planning service. */
    public LlmResponse completeJson(String systemPrompt, String userPrompt) {
        return call(systemPrompt, userPrompt, true);
    }

    /** Plain text completion. Used by the migration agents (they produce source code). */
    public LlmResponse completeText(String systemPrompt, String userPrompt) {
        return call(systemPrompt, userPrompt, false);
    }

    @SuppressWarnings("unchecked")
    private LlmResponse call(String systemPrompt, String userPrompt, boolean jsonMode) {
        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userPrompt)
        ));
        body.put("temperature", 0.2);
        if (jsonMode) {
            body.put("response_format", Map.of("type", "json_object"));
        }

        Map<String, Object> resp;
        try {
            resp = http.post()
                    .uri("/chat/completions")
                    .body(body)
                    .retrieve()
                    .body(Map.class);
        } catch (Exception e) {
            log.error("LLM call failed: {}", e.getMessage());
            throw new RuntimeException("LLM provider unavailable: " + e.getMessage(), e);
        }
        if (resp == null || !resp.containsKey("choices")) {
            throw new RuntimeException("Malformed LLM response");
        }

        List<Map<String, Object>> choices = (List<Map<String, Object>>) resp.get("choices");
        if (choices.isEmpty()) throw new RuntimeException("LLM returned no choices");
        Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
        String content = (String) message.get("content");

        Integer prompt = null, output = null;
        if (resp.get("usage") instanceof Map<?, ?> usage) {
            Object pt = usage.get("prompt_tokens");
            Object ct = usage.get("completion_tokens");
            if (pt instanceof Number n) prompt = n.intValue();
            if (ct instanceof Number n) output = n.intValue();
        }

        return new LlmResponse(content, prompt, output);
    }

    public String describe() {
        return "%s @ %s".formatted(model, baseUrl);
    }

    public String getModel() {
        return model;
    }

    public record LlmResponse(String content, Integer promptTokens, Integer outputTokens) {}
}
