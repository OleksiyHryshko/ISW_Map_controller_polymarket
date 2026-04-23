# Filterbot

Spring Boot Telegram bot that scans Polymarket events, extracts geo/time targets with Gemini, tracks targets in Google Sheets, and alerts when ISW map polygons intersect tracked locations.

## What This Project Does

- Scans Polymarket for relevant active markets (`MarketScanner`)
- Uses Gemini API to parse city, GPS coordinates, and deadline from market text (`GeminiParser`)
- Sends Telegram alerts with quick `/add` commands (`Commands`)
- Stores active targets in memory + Google Sheets (`TargetManager`)
- Polls ISW ArcGIS metadata every minute and re-checks map updates (`GeometryRadar`)
- Runs point-in-polygon checks with JTS and alerts when a tracked location is marked occupied (`IswMapMonitor`)

## Tech Stack

- Java 21
- Spring Boot 3
- Maven
- Telegram Bots API
- Google Sheets API
- Jackson
- JTS (geometry)

## Project Structure

- `src/main/java/com/example/filterbot/Main.java` - app bootstrap + scheduler enablement
- `src/main/java/com/example/filterbot/bot/Commands.java` - Telegram command handling (`/start`, `/add`, `/list`, `/delete`)
- `src/main/java/com/example/filterbot/service/MarketScanner.java` - Polymarket market discovery (scheduled)
- `src/main/java/com/example/filterbot/service/GeminiParser.java` - AI extraction for city/coords/deadline
- `src/main/java/com/example/filterbot/service/GeometryRadar.java` - map metadata polling trigger
- `src/main/java/com/example/filterbot/service/IswMapMonitor.java` - polygon intersection checks and alerts
- `src/main/java/com/example/filterbot/service/TargetManager.java` - target persistence + cleanup

## Quick Start

1. Copy template config:

```bash
cp src/main/resources/application.properties.example src/main/resources/application.properties
```

2. Export required environment variables:

```bash
export TELEGRAM_API_ID=your_value
export TELEGRAM_API_HASH=your_value
export CHAT_ID=your_value
export BOT_API=your_telegram_bot_token
export GEMINI_API_KEY=your_gemini_key
```

3. Add Google service account file locally (do not commit):

```bash
mkdir -p src/main/resources
cp /path/to/your/google-credentials.json src/main/resources/google-credentials.json
```

4. Run:

```bash
./mvnw spring-boot:run
```

## Security Notes Before Publishing

- Never commit `src/main/resources/google-credentials.json`
- Never commit `tdlib-session/` files
- Keep all API tokens in environment variables
- Rotate keys if they were previously committed to Git history

To untrack already committed sensitive/runtime files:

```bash
git rm --cached -r tdlib-session
git rm --cached src/main/resources/google-credentials.json
git rm --cached -r target
```

## Portfolio Value (for Internship)

This project demonstrates:

- Spring Boot dependency injection and scheduled jobs
- External API integration and error handling
- JSON parsing and data transformation
- Geospatial intersection logic
- Telegram bot command design
- Basic persistence and operational automation

