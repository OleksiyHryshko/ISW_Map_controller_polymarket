package com.example.filterbot.service;

import com.example.filterbot.model.TargetInfo;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class GeminiParser {

    private static final String GEMINI_BASE_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/";

    private static final String[] FALLBACK_MODELS = {
            "gemini-2.5-flash-lite",
            "gemini-1.5-flash-8b",
            "gemini-2.5-flash"
    };

    @Value("${gemini.api.key}")
    private String apiKey;

    private final HttpClient client = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    public List<TargetInfo> parseBatch(List<String> dataList) {
        if (dataList == null || dataList.isEmpty()) return new ArrayList<>();

        String combinedData = String.join("\n\n", dataList);
        String prompt = "You are a precise geographic and temporal JSON API. I will give you a list of prediction market events (Title + Description). " +
                "1. Extract the target city/region and its exact GPS coordinates. " +
                "2. Extract the deadline date/time from the description and convert it to a UNIX timestamp in MILLISECONDS. " +
                "3. Return ONLY a valid JSON array of objects. Do not use markdown tags like ```json. " +
                "Format: [{\"title\": \"Exact original Title\", \"city\": \"Vovchansk\", \"lat\": 50.29, \"lon\": 36.93, \"deadline\": 1777521540000}]. " +
                "If no clear city is found, ignore it. \nEvents:\n" + combinedData;

        try {
            String requestBody = mapper.writeValueAsString(Map.of(
                    "contents", List.of(Map.of("parts", List.of(Map.of("text", prompt))))
            ));

            for (String modelName : FALLBACK_MODELS) {
                try {
                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create(GEMINI_BASE_URL + modelName + ":generateContent"))
                            .header("Content-Type", "application/json")
                            .header("x-goog-api-key", apiKey)
                            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                            .build();

                    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                    if (response.statusCode() == 503 || response.statusCode() == 429) {
                        Thread.sleep(5000);
                        continue;
                    }

                    JsonNode candidates = mapper.readTree(response.body()).path("candidates");
                    if (candidates.isMissingNode() || candidates.isEmpty()) continue;

                    String jsonText = candidates.get(0).path("content").path("parts").get(0).path("text").asText();
                    jsonText = jsonText.replace("```json", "").replace("```", "").trim();

                    return mapper.readValue(jsonText, new TypeReference<List<TargetInfo>>() {});

                } catch (Exception e) {
                    log.warn("Gemini request failed for model {}: {}", modelName, e.getMessage());
                }
            }
            return new ArrayList<>();

        } catch (Exception e) {
            log.error("Failed to build Gemini request", e);
            return new ArrayList<>();
        }
    }
}