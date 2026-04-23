package com.example.filterbot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
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

@Slf4j
@Service
public class GeometryRadar {

    private static final String PING_URL =
            "https://services5.arcgis.com/SaBe5HMtmnbqSWlu/arcgis/rest/services/" +
            "AssessedRussianAdvanceInUkraine_V2_view/FeatureServer/0?f=json";

    private final IswMapMonitor cordcheck;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final ObjectMapper mapper = new ObjectMapper();
    private long lastCheckedTime = 0L;

    public GeometryRadar(IswMapMonitor cordcheck) {
        this.cordcheck = cordcheck;
    }

    @Scheduled(fixedRate = 60000)
    public void pingForUpdates() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(PING_URL))
                    .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15")
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.warn("ISW ping returned HTTP {}", response.statusCode());
                return;
            }

            JsonNode root = mapper.readTree(response.body());
            long currentEditDate = root.path("editingInfo").path("lastEditDate").asLong();

            if (lastCheckedTime == 0L) {
                lastCheckedTime = currentEditDate;
                log.info("Radar online. Current map version: {}", formatTimestamp(currentEditDate));
                cordcheck.checkStations();
                return;
            }

            if (currentEditDate > lastCheckedTime) {
                log.info("ISW map updated. New version: {}", formatTimestamp(currentEditDate));
                lastCheckedTime = currentEditDate;
                cordcheck.checkStations();
            } else {
                log.debug("ISW map unchanged. Last update: {}", formatTimestamp(currentEditDate));
            }

        } catch (Exception e) {
            log.error("ISW ping error", e);
        }
    }

    private String formatTimestamp(long timestamp) {
        return Instant.ofEpochMilli(timestamp)
                .atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("HH:mm:ss dd-MM-yyyy"));
    }
}