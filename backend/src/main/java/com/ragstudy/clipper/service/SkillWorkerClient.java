package com.ragstudy.clipper.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Map;

@Component
public class SkillWorkerClient {

    private final RestClient restClient;

    public SkillWorkerClient(@Value("${rag-study.skill-worker.url:http://skill-worker:8000}") String workerUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(workerUrl)
                .requestFactory(ClientHttpRequestFactories.withTimeouts(Duration.ofSeconds(10), Duration.ofHours(2)))
                .build();
    }

    public SkillRunResponse run(String skillName, Map<String, Object> input, int timeoutSeconds) {
        SkillRunRequest request = new SkillRunRequest(skillName, input, timeoutSeconds);
        return restClient.post()
                .uri("/run")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(SkillRunResponse.class);
    }

    private record SkillRunRequest(String skillName, Map<String, Object> input, int timeoutSeconds) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SkillRunResponse(
            boolean success,
            String skillName,
            JsonNode result,
            String logs,
            String error,
            double elapsedSeconds
    ) {
    }
}
