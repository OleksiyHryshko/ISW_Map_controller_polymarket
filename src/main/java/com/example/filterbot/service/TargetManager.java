package com.example.filterbot.service;

import com.example.filterbot.model.TargetInfo;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.model.ValueRange;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class TargetManager {

    private final Map<String, TargetInfo> activeTargets = new ConcurrentHashMap<>();

    @Value("${google.sheets.spreadsheet.id}")
    private String spreadsheetId;

    private Sheets getSheetsService() {
        try {
            InputStream in = getClass().getResourceAsStream("/google-credentials.json");
            if (in == null) throw new RuntimeException("google-credentials.json not found in resources");

            GoogleCredentials credentials = GoogleCredentials.fromStream(in)
                    .createScoped(Collections.singletonList("https://www.googleapis.com/auth/spreadsheets"));

            return new Sheets.Builder(
                    GoogleNetHttpTransport.newTrustedTransport(),
                    GsonFactory.getDefaultInstance(),
                    new HttpCredentialsAdapter(credentials))
                    .setApplicationName("Polymarket Radar")
                    .build();
        } catch (Exception e) {
            log.error("Google Sheets authorization failed: {}", e.getMessage());
            return null;
        }
    }

    @PostConstruct
    public void init() {
        log.info("Connecting to Google Sheets...");
        loadTargetsFromSheets();
    }

    public void addTarget(TargetInfo target) {
        activeTargets.put(target.city, target);
        saveTargetToSheets(target);
        log.info("Target added: {}", target.city);
    }

    private void saveTargetToSheets(TargetInfo t) {
        try {
            Sheets service = getSheetsService();
            if (service == null) return;

            List<Object> row = List.of(t.city, t.lat, t.lon, t.tokenId, t.deadline);
            ValueRange body = new ValueRange().setValues(List.of(row));

            service.spreadsheets().values()
                    .append(spreadsheetId, "Sheet1!A1", body)
                    .setValueInputOption("RAW")
                    .execute();
        } catch (Exception e) {
            log.error("Failed to write target to Sheets: {}", e.getMessage());
        }
    }

    public void removeTarget(String city) {
        if (activeTargets.remove(city) != null) {
            log.info("Target removed: {}", city);
        }
    }

    public List<TargetInfo> getActiveTargets() {
        return new ArrayList<>(activeTargets.values());
    }

    private void loadTargetsFromSheets() {
        try {
            Sheets service = getSheetsService();
            if (service == null) return;

            ValueRange response = service.spreadsheets().values()
                    .get(spreadsheetId, "Sheet1!A2:E")
                    .execute();

            List<List<Object>> values = response.getValues();
            if (values == null || values.isEmpty()) {
                log.info("Google Sheet is empty, no targets to load.");
                return;
            }

            for (List<Object> row : values) {
                if (row.size() < 5) continue;
                String city = row.get(0).toString();
                double lat = Double.parseDouble(row.get(1).toString());
                double lon = Double.parseDouble(row.get(2).toString());
                String tokenId = row.get(3).toString();
                long deadline = Long.parseLong(row.get(4).toString());
                activeTargets.put(city, new TargetInfo(city, lat, lon, tokenId, deadline));
            }
            log.info("Loaded {} targets from Google Sheets.", activeTargets.size());

        } catch (Exception e) {
            log.error("Failed to load targets from Sheets: {}", e.getMessage());
        }
    }

    @Scheduled(fixedRate = 3600000)
    public void cleanUpExpiredTargets() {
        long now = System.currentTimeMillis();
        activeTargets.entrySet().removeIf(entry -> entry.getValue().deadline < now);
        log.debug("Expired target cleanup completed.");
    }
}