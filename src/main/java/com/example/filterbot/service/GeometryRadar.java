package com.example.filterbot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Service
public class GeometryRadar {

    // Proper DI wiring: Spring injects this dependency
    private final IswMapMonitor cordcheck;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final ObjectMapper mapper = new ObjectMapper();
    private long lastCheckedTime = 0L;

    // Lightweight metadata endpoint (layer 0)
    private final String PING_URL = "https://services5.arcgis.com/SaBe5HMtmnbqSWlu/arcgis/rest/services/AssessedRussianAdvanceInUkraine_V2_view/FeatureServer/0?f=json";

    // Dependency injection constructor
    public GeometryRadar(IswMapMonitor cordcheck) {
        this.cordcheck = cordcheck;
    }

    @Scheduled(fixedRate = 60000) // Check every minute
    public void pingForUpdates() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(PING_URL))
                    .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15")
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode root = mapper.readTree(response.body());

                long currentEditDate = root.path("editingInfo").path("lastEditDate").asLong();

                // First run
                if (lastCheckedTime == 0L) {
                    lastCheckedTime = currentEditDate;
                    System.out.println("Radar online. Current map version: " + formatTimestamp(currentEditDate));
                    cordcheck.checkStations();
                    return;
                }

                // Trigger scan when the map has changed
                if (currentEditDate > lastCheckedTime) {
                    System.out.println("ISW map updated. New time: " + formatTimestamp(currentEditDate));
                    lastCheckedTime = currentEditDate;

                    cordcheck.checkStations();
                } else {
                    System.out.println("No changes yet. Waiting... (" + formatTimestamp(currentEditDate) + ")");
                }

            } else {
                System.err.println("Server error during ping: " + response.statusCode());
            }

        } catch (Exception e) {
            System.err.println("Network error (ping): " + e.getMessage());
        }
    }

    private String formatTimestamp(long timestamp) {
        return Instant.ofEpochMilli(timestamp)
                .atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("HH:mm:ss dd-MM-yyyy"));
    }
}