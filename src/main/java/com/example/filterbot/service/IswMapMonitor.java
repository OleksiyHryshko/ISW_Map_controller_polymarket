package com.example.filterbot.service;

import com.example.filterbot.bot.Commands;
import com.example.filterbot.model.TargetInfo;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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


@Service
public class IswMapMonitor {

    private final Commands telegramBot;
    // In-memory target store
    private final TargetManager targetManager;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    private final ObjectMapper mapper = new ObjectMapper();

    private final String COORD_URL = "https://services5.arcgis.com/SaBe5HMtmnbqSWlu/arcgis/rest/services/AssessedRussianAdvanceInUkraine_V2_view/FeatureServer/0/query?f=json&where=1%3D1&returnGeometry=true&outFields=OBJECTID&outSR=102100";

    // Spring injects both bot and target manager
    public IswMapMonitor(Commands telegramBot, TargetManager targetManager) {
        this.telegramBot = telegramBot;
        this.targetManager = targetManager;
    }

    private Coordinate gpsToWebMercator(double lon, double lat) {
        double x = lon * 20037508.34 / 180.0;
        double y = Math.log(Math.tan((90.0 + lat) * Math.PI / 360.0)) / (Math.PI / 180.0);
        y = y * 20037508.34 / 180.0;
        return new Coordinate(x, y);
    }

    public void checkStations() {
        System.out.println("ISW radar active. Scanning frontline polygons...");

        // If no targets exist, skip map download
        List<TargetInfo> activeTargets = targetManager.getActiveTargets();
        if (activeTargets.isEmpty()) {
            System.out.println("Radar is empty. No targets to validate on ISW map.");
            return;
        }

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(COORD_URL))
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            JsonNode features = null;
            boolean success = false;

            while (!success) {
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    JsonNode root = mapper.readTree(response.body());

                    if (root.has("error")) {
                        System.out.println("Server rate-limited the request (429). Waiting 15 seconds...");
                        Thread.sleep(15000);
                        continue;
                    }

                    features = root.get("features");
                    if (features == null || !features.isArray()) return;
                    success = true;
                    System.out.println("Map loaded. Polygon count: " + features.size());

                } else {
                    Thread.sleep(15000);
                }
            }

            // Geometry check loop across all tracked targets
            GeometryFactory gf = new GeometryFactory();

            // Process each target city from memory
            for (TargetInfo target : activeTargets) {

                Coordinate targetCoord = gpsToWebMercator(target.lon, target.lat);
                Point targetPoint = gf.createPoint(targetCoord);

                boolean isOccupied = false;
                String occupiedPolygonId = "";

                // Check whether the city intersects at least one polygon
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
                            break;
                        }
                    }
                    if (isOccupied) break;
                }

                // Result and Telegram alerts
                if (isOccupied) {
                    // Alert only while contract is still valid
                    if (System.currentTimeMillis() < target.deadline) {
                        String alertText = "POLYMARKET ALERT: **" + target.city.toUpperCase() + "** is marked OCCUPIED on ISW map!\n" +
                                "Contract deadline has not passed yet.\n" +
                                "Buy the 'YES' contract now.\n" +
                                "Token ID: `" + target.tokenId + "`";

                        System.out.println("ALERT: " + target.city);
                        telegramBot.sendAlertToAll(alertText);

                        // Optional: remove target after first alert to avoid repeated notifications.
                        // targetManager.removeTarget(target.city);
                    } else {
                        System.out.println("Warning: " + target.city + " is occupied, but deadline (" + target.deadline + ") has already passed.");
                    }
                } else {
                    System.out.println(target.city + " is currently free.");
                }
            }
            System.out.println("------------------------------------------------");

        } catch (Exception e) {
            System.err.println("Scanner error: " + e.getMessage());
        }
    }
}