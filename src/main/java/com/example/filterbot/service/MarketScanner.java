package com.example.filterbot.service;

import com.example.filterbot.bot.Commands;
import com.example.filterbot.model.TargetInfo;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;
import java.text.SimpleDateFormat;

@Service
public class MarketScanner {

    private final GeminiParser gemini;
    private final Commands telegram;
    private final HttpClient client = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();
    private final Set<String> knownMarkets = new HashSet<>();

    private static class PendingMarket {
        String eventSlug;
        String yesTokenId;
        String marketId;
        String description;
    }

    public MarketScanner(GeminiParser gemini, Commands telegram) {
        this.gemini = gemini;
        this.telegram = telegram;
    }

    @Scheduled(fixedRate = 6 * 3600000)
    public void scanNewMarkets() {
        System.out.println("Polymarket radar is active. Searching for new markets...");

        // Wider search: include related tags
        String[] tags = {"Russia", "Ukraine", "Putin", "Europe"};
        Map<String, PendingMarket> pendingMarkets = new HashMap<>();

        try {
            for (String tag : tags) {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create("https://gamma-api.polymarket.com/events?tag_slug=" + tag + "&active=true&closed=false"))
                        .GET().build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                JsonNode events = mapper.readTree(response.body());

                if (events == null || !events.isArray()) continue;

                for (JsonNode event : events) {
                    JsonNode markets = event.path("markets");
                    String eventSlug = event.path("slug").asText();

                    if (markets.isMissingNode() || !markets.isArray()) continue;

                    for (JsonNode market : markets) {
                        String marketId = market.path("id").asText();
                        String title = market.path("question").asText();
                        String desc = market.path("description").asText();

                        // Filter by keywords to reduce noise
                        if ((title.toLowerCase().contains("capture") || desc.toLowerCase().contains("isw"))
                                && !knownMarkets.contains(marketId)) {

                            String yesTokenId = "TOKEN_NOT_FOUND";
                            try {
                                JsonNode tokensArray = mapper.readTree(market.path("clobTokenIds").asText());
                                if (tokensArray.isArray() && !tokensArray.isEmpty()) {
                                    yesTokenId = tokensArray.get(0).asText();
                                }
                            } catch (Exception ex) {}

                            PendingMarket pm = new PendingMarket();
                            pm.eventSlug = eventSlug;
                            pm.yesTokenId = yesTokenId;
                            pm.marketId = marketId;
                            pm.description = desc;
                            pendingMarkets.put(title, pm);
                        }
                    }
                }
            }

            if (!pendingMarkets.isEmpty()) {
                System.out.println("New candidate markets found: " + pendingMarkets.size() + ". Sending to AI...");

                List<String> promptData = new ArrayList<>();
                for (Map.Entry<String, PendingMarket> entry : pendingMarkets.entrySet()) {
                    String cleanDesc = entry.getValue().description.replace("\n", " ");
                    promptData.add("Title: " + entry.getKey() + " | Description: " + cleanDesc);
                }

                List<TargetInfo> aiResults = gemini.parseBatch(promptData);

                for (TargetInfo target : aiResults) {
                    PendingMarket pm = pendingMarkets.get(target.title.replace("Title: ", ""));

                    if (pm == null) {
                        for (String key : pendingMarkets.keySet()) {
                            if (key.contains(target.title) || target.title.contains(key)) {
                                pm = pendingMarkets.get(key);
                                break;
                            }
                        }
                    }

                    if (pm != null) {
                        String cityName = target.city.replaceAll("\\s+", "_");
                        String marketUrl = "https://polymarket.com/event/" + pm.eventSlug;
                        SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.US);
                        String humanReadableDate = sdf.format(new Date(target.deadline));
                        String alert = String.format(
                                "New Polymarket market detected!\n" +
                                        "Location: %s\n" +
                                        "Coordinates: %s, %s\n" +
                                        "Deadline: **%s**\n" +
                                        "[Event link](%s)\n\n" +
                                        "To add it to radar, send this command:\n" +
                                        "`/add %s %s %s %s %d`",
                                cityName, target.lat, target.lon, humanReadableDate, marketUrl,
                                cityName, target.lat, target.lon, pm.yesTokenId, target.deadline
                        );
                        telegram.sendAlertToAll(alert);
                        knownMarkets.add(pm.marketId);
                    }
                }

                for (PendingMarket pm : pendingMarkets.values()) {
                    knownMarkets.add(pm.marketId);
                }
            } else {
                System.out.println("No new markets found.");
            }

        } catch (Exception e) {
            System.err.println("Polymarket scan error: " + e.getMessage());
        }
    }
}