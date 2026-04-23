package com.example.filterbot.bot;

import com.example.filterbot.model.TargetInfo;
import com.example.filterbot.service.TargetManager;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

import java.util.List;

@Slf4j
@Component
public class Commands extends TelegramLongPollingBot {

    @Value("${bot.allowed.chat.ids}")
    private List<Long> allowedChatIds;

    @Value("${bot.username}")
    private String botUsername;

    private final TargetManager targetManager;

    public Commands(@Value("${botapi}") String botToken, TargetManager targetManager) {
        super(botToken);
        this.targetManager = targetManager;
    }

    @PostConstruct
    public void startListening() {
        try {
            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
            botsApi.registerBot(this);
            log.info("Telegram bot connected and listening for commands.");
        } catch (Exception e) {
            log.warn("Telegram bot registration: {}", e.getMessage());
        }
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (!update.hasMessage() || !update.getMessage().hasText()) return;

        long chatId = update.getMessage().getChatId();
        String messageText = update.getMessage().getText().trim();

        if (!allowedChatIds.contains(chatId)) {
            log.warn("Unauthorized access attempt from chat ID: {}", chatId);
            sendMsg(chatId, "Access denied. Your ID: " + chatId);
            return;
        }

        try {
            log.debug("Command received from {}: {}", chatId, messageText);

            if (messageText.equals("/start")) {
                sendMsg(chatId, "Polymarket Eagles Radar is online. Access granted.\n\n" +
                        "Commands:\n" +
                        "- `/list` - list active targets\n" +
                        "- `/delete Name` - remove a target from radar\n" +
                        "- `/add ...` - add target (use ready command from alerts)");
            } else if (messageText.startsWith("/add ")) {
                handleAddCommand(chatId, messageText);
            } else if (messageText.equals("/list")) {
                handleListCommand(chatId);
            } else if (messageText.startsWith("/delete ")) {
                handleDeleteCommand(chatId, messageText);
            } else {
                sendMsg(chatId, "Unknown command. Send /start for the menu.");
            }
        } catch (Exception e) {
            log.error("Command processing error", e);
            sendMsg(chatId, "Command processing error: " + e.getMessage());
        }
    }

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

            targetManager.addTarget(new TargetInfo(city, lat, lon, token, deadline));
            sendMsg(chatId, "Target **" + city + "** added to radar and saved to storage.");
        } catch (NumberFormatException e) {
            sendMsg(chatId, "Number format error. Check coordinates and deadline.");
        }
    }

    private void handleListCommand(long chatId) {
        List<TargetInfo> targets = targetManager.getActiveTargets();
        if (targets.isEmpty()) {
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
            log.error("Failed to send Telegram message to {}", chatId, e);
        }
    }

    public void sendAlertToAll(String alertText) {
        allowedChatIds.forEach(id -> sendMsg(id, alertText));
    }

    @Override
    public String getBotUsername() {
        return botUsername;
    }
}