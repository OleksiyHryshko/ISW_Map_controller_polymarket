package com.example.filterbot.bot;

import com.example.filterbot.model.TargetInfo;
import com.example.filterbot.service.TargetManager;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

import java.util.List;

@Component
public class Commands extends TelegramLongPollingBot {

    // Allowlist
    private final List<Long> allowedUsersId = List.of(751377288L);

    // Target manager
    private final TargetManager targetManager;

    public Commands(@Value("${botapi}") String botToken, TargetManager targetManager) {
        super(botToken);
        this.targetManager = targetManager;
    }

    // Force-start bot listener
    @PostConstruct
    public void startListening() {
        try {
            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
            botsApi.registerBot(this);
            System.out.println("Telegram bot connected successfully and is listening for commands.");
        } catch (Exception e) {
            System.out.println("Telegram listener status: " + e.getMessage());
        }
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            long chatId = update.getMessage().getChatId();
            String messageText = update.getMessage().getText().trim();

            // Security check: log who is writing to us (helps verify your own ID)
            if (!allowedUsersId.contains(chatId)) {
                System.out.println("Unauthorized access attempt from ID: " + chatId + " | Text: " + messageText);
                sendMsg(chatId, "Access denied. Your ID: " + chatId);
                return;
            }

            try {
                System.out.println("Command received: " + messageText);

                // Command handling block
                if (messageText.equals("/start")) {
                    sendMsg(chatId, "Polymarket Eagles Radar is online. Access granted.\n\n" +
                            "Commands:\n" +
                            "- `/list` - list active targets\n" +
                            "- `/delete Name` - remove a target from radar\n" +
                            "- `/add ...` - add target (use ready command from alerts)");
                }
                else if (messageText.startsWith("/add ")) {
                    handleAddCommand(chatId, messageText);
                }
                else if (messageText.equals("/list")) {
                    handleListCommand(chatId);
                }
                else if (messageText.startsWith("/delete ")) {
                    handleDeleteCommand(chatId, messageText);
                }
                else {
                    sendMsg(chatId, "Unknown command. Send /start for the menu.");
                }
            } catch (Exception e) {
                sendMsg(chatId, "Command processing error: " + e.getMessage());
            }
        }
    }

    // 1. /add command
    private void handleAddCommand(long chatId, String message) {
        String[] parts = message.split("\\s+");
        if (parts.length != 6) {
            sendMsg(chatId, "Invalid format. Copy the full command from the alert message.");
            return;
        }

        try {
            String city = parts[1];
            double lat = Double.parseDouble(parts[2]);
            double lon = Double.parseDouble(parts[3]);
            String token = parts[4];
            long deadline = Long.parseLong(parts[5]);

            TargetInfo newTarget = new TargetInfo(city, lat, lon, token, deadline);
            targetManager.addTarget(newTarget);

            sendMsg(chatId, "Target **" + city + "** was added to the radar and saved to storage.");
        } catch (NumberFormatException e) {
            sendMsg(chatId, "Number format error. Check coordinates and deadline.");
        }
    }

    // 2. /list command
    private void handleListCommand(long chatId) {
        List<TargetInfo> targets = targetManager.getActiveTargets();
        if (targets == null || targets.isEmpty()) {
            sendMsg(chatId, "Radar is empty. There are no active targets.");
            return;
        }

        StringBuilder sb = new StringBuilder("Active radar targets (" + targets.size() + "):\n\n");
        for (TargetInfo t : targets) {
            sb.append("Location: **").append(t.city).append("**\n")
                    .append("Coordinates: ").append(t.lat).append(", ").append(t.lon).append("\n")
                    .append("Deadline (UNIX): ").append(t.deadline).append("\n")
                    .append("------------------------\n");
        }
        sendMsg(chatId, sb.toString());
    }

    // 3. /delete command
    private void handleDeleteCommand(long chatId, String message) {
        String[] parts = message.split("\\s+", 2);
        if (parts.length < 2) {
            sendMsg(chatId, "Specify a city to delete. Example:\n`/delete Kupiansk`");
            return;
        }

        String city = parts[1];
        targetManager.removeTarget(city);
        sendMsg(chatId, "Target **" + city + "** removed from radar.");
    }

    public void sendMsg(long chatId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(text);
        message.setParseMode("Markdown");
        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    public void sendAlertToAll(String alertText) {
        for (Long userId : allowedUsersId) {
            sendMsg(userId, alertText);
        }
    }

    @Override
    public String getBotUsername() {
        // IMPORTANT: replace with your bot username, without @
        // Example: "OleksiiRadar_bot"
        return "@polymarket_eaglebot";
    }
}