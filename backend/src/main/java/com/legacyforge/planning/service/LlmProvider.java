package com.legacyforge.planning.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Calls an OpenAI-compatible /chat/completions endpoint for planning.
 *
 * Configured via env:
 *   - PLANNING_BASE_URL  (e.g. http://localhost:11434/v1  or  https://generativelanguage.googleapis.com/v1beta/openai)
 *   - PLANNING_API_KEY   (dummy for Ollama, real key for Gemini)
 *   - PLANNING_MODEL     (e.g. llama3.2  or  gemini-2.5-flash)
 *
 * Uses JSON mode (response_format=json_object) so the model always returns parseable JSON.
 * Timeout is long because planning prompts include the whole codebase and can take 30-60s.
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
        rf.setReadTimeout((int) Duration.ofSeconds(120).toMillis());

        this.http = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(rf)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();
        log.info("LlmProvider: base={} model={}", baseUrl, model);
    }

    public LlmResponse completeJson(String systemPrompt, String userPrompt) {
        Map<String, Object> body = Map.of(
                "model", model,
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userPrompt)
                ),
                "response_format", Map.of("type", "json_object"),
                "temperature", 0.2
        );

        Map<String, Object> resp;
        try {
            resp = http.post()
                    .uri("/chat/completions")
                    .body(body)
                    .retrieve()
                    .body(Map.class);
        } catch (Exception e) {
            log.error("LLM call failed: {}", e.getMessage());
            throw new RuntimeException("Planning provider unavailable: " + e.getMessage(), e);
        }
        if (resp == null || !resp.containsKey("choices")) {
            throw new RuntimeException("Malformed LLM response");
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> choices = (List<Map<String, Object>>) resp.get("choices");
        if (choices.isEmpty()) throw new RuntimeException("LLM returned no choices");
        @SuppressWarnings("unchecked")
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
