# FilterBot - Polymarket Event Monitor

A Spring Boot Telegram bot that monitors Polymarket events, extracts geospatial targets using Google Gemini AI, tracks them in Google Sheets, and sends real-time alerts when map data changes indicate target locations have been affected.

## Key Features

- **Market Discovery**: Scans Polymarket API for relevant active markets
- **AI-Powered Parsing**: Uses Google Gemini API to extract location names, GPS coordinates, and deadlines from market descriptions
- **Telegram Integration**: Sends alerts and accepts commands via Telegram bot interface
- **Geospatial Tracking**: Stores and manages targets across memory and Google Sheets
- **Map Monitoring**: Polls ISW ArcGIS map data every minute for updates
- **Spatial Analysis**: Uses JTS library for point-in-polygon intersection detection to identify when tracked locations are affected

## Tech Stack

| Technology | Purpose |
|------------|---------|
| **Java 21** | Core language with modern features |
| **Spring Boot 3** | Application framework with scheduling support |
| **Maven** | Build and dependency management |
| **Telegram Bots API** | Real-time bot communications |
| **Google Gemini API** | NLP for event text analysis |
| **Google Sheets API** | Data persistence and visualization |
| **JTS (Java Topology Suite)** | Geospatial polygon intersection calculations |
| **Jackson** | JSON serialization/deserialization |
| **TDLight** | Telegram client library (native bindings) |

## Architecture & Components

### Core Classes

| Class | Responsibility |
|-------|-----------------|
| `Main.java` | Application bootstrap & Spring scheduling enablement |
| `Commands.java` | Telegram bot command handlers (`/start`, `/add`, `/list`, `/delete`) |
| `MarketScanner.java` | Scheduled task that fetches and filters Polymarket markets |
| `GeminiParser.java` | AI-powered text analysis to extract location & time data |
| `TargetManager.java` | In-memory target storage with Google Sheets synchronization |
| `GeometryRadar.java` | Polling mechanism for ISW map metadata updates |
| `IswMapMonitor.java` | Performs intersection analysis and sends Telegram alerts |
| `TargetInfo.java` | Data model for geospatial targets |

### Execution Flow

```
MarketScanner (60s interval)
    ↓
Fetches markets → GeminiParser → Extracts location/deadline
    ↓
TargetManager (in-memory + Google Sheets)
    ↓
GeometryRadar (60s interval) → IswMapMonitor
    ↓
Point-in-polygon checks → Telegram alerts
```

## Setup & Installation

### Prerequisites
- Java 21+
- Maven 3.6+
- Valid Telegram bot token (from BotFather)
- Google Cloud project with Gemini API & Sheets API enabled
- Google service account with Sheets access

### Configuration Steps

**1. Clone and navigate to the project:**
```bash
git clone <repo-url>
cd filterbot
```

**2. Set up environment variables:**
```bash
# Telegram Configuration
export TELEGRAM_API_ID=your_telegram_api_id
export TELEGRAM_API_HASH=your_telegram_api_hash
export BOT_API=your_bot_token_from_botfather
export CHAT_ID=your_telegram_chat_id

# Google APIs
export GEMINI_API_KEY=your_google_gemini_api_key
export GOOGLE_SHEETS_SPREADSHEET_ID=your_spreadsheet_id
```

**3. Set up Google credentials:**
```bash
# Create a service account in Google Cloud Console
# Download the credentials JSON file
mkdir -p src/main/resources
cp /path/to/google-credentials.json src/main/resources/google-credentials.json
```

**4. Build and run:**
```bash
# Build the project
./mvnw clean package

# Run the application
./mvnw spring-boot:run
```

### Using .env File (Optional)
Create a `.env` file in the project root (excluded from git):
```
TELEGRAM_API_ID=your_value
TELEGRAM_API_HASH=your_value
BOT_API=your_value
CHAT_ID=your_value
GEMINI_API_KEY=your_value
GOOGLE_SHEETS_SPREADSHEET_ID=your_value
```

Then source it before running:
```bash
source .env && ./mvnw spring-boot:run
```

## Security Best Practices

✅ **What's Protected:**
- Google service account credentials are git-ignored
- All API tokens stored in environment variables
- Session files and compiled artifacts excluded from version control
- No hardcoded secrets in source code

⚠️ **Important:**
- Never commit `google-credentials.json` to version control
- Rotate API keys if accidentally exposed
- Use environment variables or secure vaults in production
- Keep `.env` files local and untracked

## Learning Outcomes & Skills Demonstrated

This project showcases full-stack Java development competencies:

### Backend Architecture
- ✓ Spring Boot application design with dependency injection
- ✓ Scheduled task execution (@Scheduled) for background processes
- ✓ Multi-threaded data processing and synchronization

### External API Integration
- ✓ RESTful API consumption (Polymarket market data)
- ✓ Asynchronous API calls with error handling
- ✓ Authentication with multiple third-party services (Google, Telegram)

### Data Processing & Analysis
- ✓ JSON parsing and transformation using Jackson
- ✓ Natural language processing integration (Gemini AI)
- ✓ Geospatial calculations (point-in-polygon intersection)
- ✓ Database operations (Google Sheets as data store)

### Real-time Communication
- ✓ Telegram bot framework integration
- ✓ Command routing and message handling
- ✓ Event-driven alerts and notifications

### Software Engineering Practices
- ✓ Clean code architecture with separation of concerns
- ✓ Configuration management through properties files
- ✓ Version control and git workflows
- ✓ Secure credential handling

## Project Structure

```
filterbot/
├── src/
│   ├── main/
│   │   ├── java/com/example/filterbot/
│   │   │   ├── Main.java                 # Application entry point
│   │   │   ├── bot/
│   │   │   │   └── Commands.java         # Telegram command handler
│   │   │   ├── model/
│   │   │   │   └── TargetInfo.java       # Target data model
│   │   │   └── service/
│   │   │       ├── MarketScanner.java    # Market discovery (scheduled)
│   │   │       ├── GeminiParser.java     # AI text extraction
│   │   │       ├── TargetManager.java    # Target persistence
│   │   │       ├── GeometryRadar.java    # Map polling trigger
│   │   │       └── IswMapMonitor.java    # Spatial analysis & alerts
│   │   └── resources/
│   │       ├── application.properties
│   │       └── application.properties.example
│   └── test/                              # Test suite
├── pom.xml                                # Maven configuration
├── mvnw, mvnw.cmd                        # Maven wrapper
└── README.md                              # This file
```

## Contributing

Feel free to fork and submit pull requests for improvements.

## License

This project is provided as-is for educational and portfolio purposes.

---

**Note**: This is a portfolio project demonstrating Java/Spring Boot competencies for internship applications. The codebase emphasizes clean architecture, proper API integration, and secure credential handling.

