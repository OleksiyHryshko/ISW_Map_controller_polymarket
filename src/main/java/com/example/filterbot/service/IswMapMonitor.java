package com.example.filterbot.service;

import com.example.filterbot.bot.Commands;
import com.example.filterbot.model.TargetInfo;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class IswMapMonitor {

    private static final int MAX_FETCH_RETRIES = 5;
    private static final String COORD_URL =
            "https://services5.arcgis.com/SaBe5HMtmnbqSWlu/arcgis/rest/services/" +
            "AssessedRussianAdvanceInUkraine_V2_view/FeatureServer/0/query" +
            "?f=json&where=1%3D1&returnGeometry=true&outFields=OBJECTID&outSR=102100";

    private final Commands telegramBot;
    private final TargetManager targetManager;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    private final ObjectMapper mapper = new ObjectMapper();

    public IswMapMonitor(Commands telegramBot, TargetManager targetManager) {
        this.telegramBot = telegramBot;
        this.targetManager = targetManager;
    }

    public void checkStations() {
        log.info("Scanning ISW frontline polygons...");

        List<TargetInfo> activeTargets = targetManager.getActiveTargets();
        if (activeTargets.isEmpty()) {
            log.info("No active targets. Skipping ISW map download.");
            return;
        }

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(COORD_URL))
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            JsonNode features = fetchWithRetry(request);
            if (features == null) {
                log.error("Failed to fetch ISW map data after {} attempts.", MAX_FETCH_RETRIES);
                return;
            }

            log.info("ISW map loaded. Polygon count: {}", features.size());
            GeometryFactory gf = new GeometryFactory();

            for (TargetInfo target : activeTargets) {
                Coordinate targetCoord = gpsToWebMercator(target.lon, target.lat);
                Point targetPoint = gf.createPoint(targetCoord);
                boolean isOccupied = false;
                String occupiedPolygonId = "";

                outer:
                for (JsonNode feature : features) {
                    JsonNode geometry = feature.get("geometry");
                    if (geometry == null || !geometry.has("rings")) continue;

                    for (JsonNode ring : geometry.get("rings")) {
                        List<Coordinate> coordsList = new ArrayList<>();
                        for (JsonNode pointNode : ring) {
                            coordsList.add(new Coordinate(pointNode.get(0).asDouble(), pointNode.get(1).asDouble()));
                        }
                        Coordinate[] coordsArray = coordsList.toArray(new Coordinate[0]);
                        if (coordsArray.length < 4) continue;

                        Polygon polygon = gf.createPolygon(gf.createLinearRing(coordsArray), null);
                        if (polygon.intersects(targetPoint)) {
                            isOccupied = true;
                            occupiedPolygonId = feature.path("attributes").path("OBJECTID").asText();
                            break outer;
                        }
                    }
                }

                if (isOccupied) {
                    if (System.currentTimeMillis() < target.deadline) {
                        String alertText = "POLYMARKET ALERT: **" + target.city.toUpperCase() + "** is marked OCCUPIED on ISW map!\n" +
                                "Contract deadline has not passed yet.\n" +
                                "Buy the 'YES' contract now.\n" +
                                "Token ID: `" + target.tokenId + "`";
                        log.info("ALERT triggered for {}", target.city);
                        telegramBot.sendAlertToAll(alertText);
                    } else {
                        log.warn("{} is occupied but deadline {} has already passed.", target.city, target.deadline);
                    }
                } else {
                    log.debug("{} is currently free.", target.city);
                }
            }

        } catch (Exception e) {
            log.error("ISW map scan error", e);
        }
    }

    private JsonNode fetchWithRetry(HttpRequest request) throws InterruptedException {
        for (int attempt = 1; attempt <= MAX_FETCH_RETRIES; attempt++) {
            try {
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() != 200) {
                    log.warn("ISW fetch attempt {}/{} returned HTTP {}. Retrying...", attempt, MAX_FETCH_RETRIES, response.statusCode());
                    Thread.sleep(15000);
                    continue;
                }
                JsonNode root = mapper.readTree(response.body());
                if (root.has("error")) {
                    log.warn("ISW fetch attempt {}/{} rate-limited. Retrying...", attempt, MAX_FETCH_RETRIES);
                    Thread.sleep(15000);
                    continue;
                }
                JsonNode features = root.get("features");
                if (features != null && features.isArray()) return features;
            } catch (Exception e) {
                log.warn("ISW fetch attempt {}/{} failed: {}", attempt, MAX_FETCH_RETRIES, e.getMessage());
                Thread.sleep(15000);
            }
        }
        return null;
    }

    private Coordinate gpsToWebMercator(double lon, double lat) {
        double x = lon * 20037508.34 / 180.0;
        double y = Math.log(Math.tan((90.0 + lat) * Math.PI / 360.0)) / (Math.PI / 180.0);
        y = y * 20037508.34 / 180.0;
        return new Coordinate(x, y);
    }
}