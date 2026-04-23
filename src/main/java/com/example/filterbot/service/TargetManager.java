package com.example.filterbot.service;

import com.example.filterbot.model.TargetInfo;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.model.ValueRange;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TargetManager {

    private final Map<String, TargetInfo> activeTargets = new ConcurrentHashMap<>();

    @Value("${google.sheets.spreadsheet.id}")
    private String spreadsheetId;

    private Sheets getSheetsService() {
        try {
            InputStream in = getClass().getResourceAsStream("/google-credentials.json");
            if (in == null) throw new RuntimeException("google-credentials.json not found");

            GoogleCredentials credentials = GoogleCredentials.fromStream(in)
                    .createScoped(Collections.singletonList("https://www.googleapis.com/auth/spreadsheets"));

            return new Sheets.Builder(
                    GoogleNetHttpTransport.newTrustedTransport(),
                    GsonFactory.getDefaultInstance(),
                    new HttpCredentialsAdapter(credentials))
                    .setApplicationName("Polymarket Radar")
                    .build();
        } catch (Exception e) {
            System.err.println("Google authorization error: " + e.getMessage());
            return null;
        }
    }

    @PostConstruct
    public void init() {
        System.out.println("Connecting to Google Sheets...");
        loadTargetsFromSheets();
    }

    // 1. Add (from Telegram bot)
    public void addTarget(TargetInfo target) {
        activeTargets.put(target.city, target);
        saveTargetToSheets(target);
        System.out.println("Target added: " + target.city);
    }

    // Append a new row to the sheet
    private void saveTargetToSheets(TargetInfo t) {
        try {
            Sheets service = getSheetsService();
            if (service == null) return;

            // Row format: City, Lat, Lon, TokenId, Deadline
            List<Object> row = List.of(t.city, t.lat, t.lon, t.tokenId, t.deadline);
            ValueRange body = new ValueRange().setValues(List.of(row));

            service.spreadsheets().values()
                    .append(spreadsheetId, "Sheet1!A1", body)
                    .setValueInputOption("RAW")
                    .execute();
        } catch (Exception e) {
            System.err.println("Failed to write to Sheets: " + e.getMessage());
        }
    }

    // 2. Remove
    public void removeTarget(String city) {
        if (activeTargets.remove(city) != null) {
            System.out.println("Target removed from memory: " + city);
            // For V1, remove rows in Sheets manually,
            // or let expired targets disappear after restart.
        }
    }

    // 3. Get list (for Commands)
    public List<TargetInfo> getActiveTargets() {
        return new ArrayList<>(activeTargets.values());
    }

    // 4. Load data at startup
    private void loadTargetsFromSheets() {
        try {
            Sheets service = getSheetsService();
            if (service == null) return;

            // Read range A2:E (skip header)
            ValueRange response = service.spreadsheets().values()
                    .get(spreadsheetId, "Sheet1!A2:E")
                    .execute();

            List<List<Object>> values = response.getValues();
            if (values == null || values.isEmpty()) {
                System.out.println("Sheet is empty.");
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
            System.out.println("Targets loaded from Sheets: " + activeTargets.size());

        } catch (Exception e) {
            System.err.println("Failed to load data: " + e.getMessage());
        }
    }

    @Scheduled(fixedRate = 3600000)
    public void cleanUpExpiredTargets() {
        long now = System.currentTimeMillis();
        activeTargets.entrySet().removeIf(entry -> entry.getValue().deadline < now);
        System.out.println("Expired target cleanup completed.");
    }
}