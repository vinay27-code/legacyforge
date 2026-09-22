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
 * Calls an OpenAI-compatible /chat/completions endpoint. Week 11: adds
 * structured timing + token logging around every call so latency and cost
 * are observable in Cloud Run logs and roll into the Stats aggregate.
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

    public LlmResponse completeJson(String systemPrompt, String userPrompt) {
        return call(systemPrompt, userPrompt, true, "json");
    }

    public LlmResponse completeText(String systemPrompt, String userPrompt) {
        return call(systemPrompt, userPrompt, false, "text");
    }

    @SuppressWarnings("unchecked")
    private LlmResponse call(String systemPrompt, String userPrompt, boolean jsonMode, String mode) {
        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userPrompt)
        ));
        body.put("temperature", 0.2);
        if (jsonMode) body.put("response_format", Map.of("type", "json_object"));

        long startNanos = System.nanoTime();
        int promptSize = systemPrompt.length() + userPrompt.length();

        Map<String, Object> resp;
        try {
            resp = http.post()
                    .uri("/chat/completions")
                    .body(body)
                    .retrieve()
                    .body(Map.class);
        } catch (Exception e) {
            long ms = (System.nanoTime() - startNanos) / 1_000_000L;
            log.error("LLM call failed mode={} model={} promptChars={} durationMs={} error={}",
                    mode, model, promptSize, ms, e.getMessage());
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

        long ms = (System.nanoTime() - startNanos) / 1_000_000L;
        log.info("llm_call mode={} model={} durationMs={} promptTokens={} outputTokens={} promptChars={}",
                mode, model, ms, prompt, output, promptSize);

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
