# MemoryForge

**MemoryForge** is a personal journaling application that uses large language
models to generate summaries, stories, recurring-theme analyses, and character
arcs from journal entries. It supports local models through
[Ollama](https://ollama.com) and hosted Google Gemini models through an API key.

When Ollama is used, journal content and generation requests remain on the
local system. When Gemini is used, journal content is sent to Google's API.
The application consists of a Scala 3 and ZIO backend, a PostgreSQL database,
and an optional single-page frontend.

---

## Features

- Create and manage journal entries with titles, content, moods, and tags.
- Generate content from an entry using multiple **modes**:
  - `reflective_summary` – reflective journal summary
  - `funny_story` – short comedic story
  - `dramatic_story` – short dramatic story
  - `anime_scene` – anime-style scene
  - `brain_rot` – meme-oriented version
  - `theme_analysis` – recurring-theme analysis
- Request structured JSON from the LLM, with a fallback to raw text when the
  response cannot be parsed.
- Separate HTTP routes, services, repositories, database access, LLM clients,
  and prompt templates.
- Handle missing entries, malformed requests, database failures, LLM failures,
  timeouts, and invalid LLM responses.
- Run PostgreSQL and the backend with Docker Compose, with optional pgAdmin.

---

## Technology

| Layer        | Technology                |
|--------------|---------------------------|
| Language     | Scala 3                   |
| Effects/HTTP | ZIO 2 + zio-http          |
| JSON         | zio-json                  |
| Database     | PostgreSQL (JDBC + HikariCP) |
| LLM          | Ollama (local) or Google Gemini |
| Packaging    | sbt-assembly + Docker     |

---

## Project structure

```
src/main/scala/memoryforge/
├── Main.scala                 # ZIO application bootstrap and layer wiring
├── config/AppConfig.scala     # Environment-based configuration
├── db/Database.scala          # Hikari pool, schema initialization, and transactions
├── domain/                    # Models, errors, and generation modes
│   ├── Models.scala
│   ├── GenerationMode.scala
│   └── AppError.scala
├── repository/                # JDBC persistence
│   ├── EntryRepository.scala
│   └── StoryRepository.scala
├── llm/                       # Provider clients and prompt templates
│   ├── LLMClient.scala
│   ├── GeminiClient.scala
│   ├── OllamaClient.scala
│   └── PromptTemplates.scala
├── service/                   # Business logic
│   ├── EntryService.scala
│   └── StoryService.scala
└── routes/HttpApi.scala       # REST endpoints
```

---

## Setup

### Prerequisites

- [Ollama](https://ollama.com) installed and running on the host, or a Google
  Gemini API key.
- A model available in Ollama, for example:
  ```bash
  ollama pull llama3.1     # or: qwen2.5
  ```
- For local (non-Docker) runs: JDK 21, sbt, and a running PostgreSQL.

### Run with Docker Compose

The default Docker configuration assumes that Ollama runs on the host machine.
The backend connects to it through `host.docker.internal`.

```bash
# Ensure Ollama is running and the model is available
ollama serve            # if not already running
ollama pull llama3.1

# Build and start PostgreSQL and the backend
docker compose up --build

# Optional: start pgAdmin at http://localhost:5050
docker compose --profile tools up --build
```

Backend will be available at <http://localhost:8080>.

### Run the backend with sbt

```bash
# Start PostgreSQL with Docker Compose, or use an existing instance
docker compose up -d db

# Set the configuration
export DB_URL=jdbc:postgresql://localhost:5432/memoryforge
export DB_USER=memoryforge
export DB_PASSWORD=memoryforge
export LLM_PROVIDER=ollama
export OLLAMA_URL=http://localhost:11434
export OLLAMA_MODEL=llama3.1

sbt run
```

To use Gemini, set the provider and API key instead:

```bash
export LLM_PROVIDER=gemini
export GEMINI_API_KEY=<your-api-key>
export GEMINI_MODEL=gemini-3.5-flash

sbt run
```

### Configuration (environment variables)

All configuration is provided through environment variables.

| Variable                 | Default                                              |
|--------------------------|------------------------------------------------------|
| `SERVER_PORT`            | `8080`                                               |
| `DB_URL`                 | `jdbc:postgresql://localhost:5432/memoryforge`       |
| `DB_USER`                | `memoryforge`                                        |
| `DB_PASSWORD`            | `memoryforge`                                        |
| `DB_POOL_SIZE`           | `10`                                                 |
| `LLM_PROVIDER`           | `ollama` (`ollama` or `gemini`)                      |
| `OLLAMA_URL`             | `http://localhost:11434`                             |
| `OLLAMA_MODEL`           | `llama3.1`                                            |
| `OLLAMA_TIMEOUT_SECONDS` | `120`                                                |
| `GEMINI_API_KEY`         | unset                                                |
| `GEMINI_URL`             | `https://generativelanguage.googleapis.com`          |
| `GEMINI_MODEL`           | `gemini-3.5-flash`                                   |
| `GEMINI_TIMEOUT_SECONDS` | `120`                                                |

---

## API

| Method | Path                              | Description                          |
|--------|-----------------------------------|--------------------------------------|
| GET    | `/health`                         | Health check                         |
| POST   | `/entries`                        | Create a journal entry               |
| GET    | `/entries`                        | List all entries                     |
| GET    | `/entries/{id}`                   | Get one entry                        |
| DELETE | `/entries/{id}`                   | Delete an entry                      |
| POST   | `/entries/{id}/generate-story`    | Generate a story (mode in body)      |
| POST   | `/entries/{id}/summarize`         | Reflective summary                   |
| POST   | `/entries/{id}/analyze-themes`    | Recurring theme analysis             |
| GET    | `/stories`                        | List all generated stories           |

### Examples

Create an entry:

```bash
curl -s -X POST http://localhost:8080/entries \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Leg day disaster",
    "content": "Tried to PR my squat today. Loaded the bar, got under it, and immediately remembered I skipped warmups. Survived, barely. Still hit 100kg though!",
    "mood": "proud but sore",
    "tags": ["gym", "progress", "squat"]
  }'
```

Generate content using the default `funny_story` mode:

```bash
curl -s -X POST http://localhost:8080/entries/<ENTRY_ID>/generate-story \
  -H "Content-Type: application/json" \
  -d '{ "mode": "funny_story" }'
```

The other supported modes are `reflective_summary`, `dramatic_story`,
`anime_scene`, `brain_rot`, and `theme_analysis`. A request can also override
the model configured for the active provider:

```json
{ "mode": "anime_scene", "model": "qwen2.5" }
```

The convenience endpoints are equivalent to requests for their corresponding
modes:

```bash
curl -s -X POST http://localhost:8080/entries/<ENTRY_ID>/summarize
curl -s -X POST http://localhost:8080/entries/<ENTRY_ID>/analyze-themes
```

List entries and generated stories:

```bash
curl -s http://localhost:8080/entries
curl -s http://localhost:8080/stories
```

Example journal entry response:

```json
{
  "id": "0f7d2c2e-2c2e-4c2e-8c2e-2c2e2c2e2c2e",
  "title": "Leg day disaster",
  "content": "Tried to PR my squat today...",
  "mood": "proud but sore",
  "tags": ["gym", "progress", "squat"],
  "createdAt": "2026-06-08T03:00:00Z",
  "updatedAt": "2026-06-08T03:00:00Z"
}
```

Example generated story response:

```json
{
  "id": "a1b2c3d4-0000-0000-0000-000000000001",
  "entryId": "0f7d2c2e-2c2e-4c2e-8c2e-2c2e2c2e2c2e",
  "title": "The Bar That Fought Back",
  "genre": "comedy",
  "style": "funny short story",
  "summary": "A lifter narrowly completes a 100kg squat after skipping warmups.",
  "story": "The lifter approached the squat rack with confidence, despite having skipped the warmup...",
  "createdAt": "2026-06-08T03:01:12Z"
}
```

---

## LLM response handling

1. `PromptTemplates` asks the configured provider for a single JSON object with
   `title`, `genre`, `style`, `summary`, and `story` fields.
2. `LLMClient` routes the request to `OllamaClient` or `GeminiClient`. Ollama
   uses `POST /api/generate` with `format: "json"`; Gemini uses the
   Interactions API with `response_format.mime_type: "application/json"`.
3. `StoryService.toStory` attempts to parse the response. If parsing fails or
   the response is empty, the raw model output is stored in the `story` field.

---

## Error handling

| Situation                | HTTP status | `error` code            |
|--------------------------|-------------|-------------------------|
| Entry id not found       | 404         | `entry_not_found`       |
| Invalid body / bad UUID  | 400         | `invalid_request`       |
| Database unavailable     | 503         | `database_error`        |
| Ollama unreachable       | 502         | `ollama_unavailable`    |
| Gemini unavailable / misconfigured | 502 | `llm_unavailable` |
| LLM timeout              | 504         | `llm_timeout`           |
| Unusable LLM response    | 502         | `invalid_llm_response`  |

---

## Optional frontend

The repository includes a zero-build Vue application in
`frontend/index.html`. Serve it with any static file server:

```bash
cd frontend
python3 -m http.server 5173
# then open http://localhost:5173
```

Open <http://localhost:5173> in a browser. The application can create entries,
select generation modes, and display generated stories. If the backend is not
running at `http://localhost:8080`, update the `API` constant in the page's
`<script>` block.

---

## Troubleshooting Ollama connectivity

When the backend runs in Docker and Ollama runs on the host, verify both of the
following conditions.

### Use the host address from the container

The backend must use `host.docker.internal`, not `localhost`. Within a
container, `localhost` refers to the container itself. The Compose file sets
`OLLAMA_URL=http://host.docker.internal:11434` and maps
`host.docker.internal` to the host gateway. If the value was changed, restore it
or set it in a `.env` file, then recreate the backend:

```bash
docker compose up -d --build --force-recreate backend
```

### Allow Ollama to accept container connections

Ollama must listen on `0.0.0.0`, rather than only on `127.0.0.1`:

```bash
# Stop any existing instance first
OLLAMA_HOST=0.0.0.0:11434 ollama serve
```

If Ollama runs as a systemd service, use `systemctl edit ollama` to add:

```ini
Environment="OLLAMA_HOST=0.0.0.0:11434"
```

Then reload the service configuration and restart Ollama:

```bash
systemctl daemon-reload
systemctl restart ollama
```

Test connectivity from a temporary container:

```bash
docker run --rm --add-host=host.docker.internal:host-gateway curlimages/curl \
  -s http://host.docker.internal:11434/api/tags
```

The response should contain the available models. Confirm that `OLLAMA_MODEL`
matches the model name shown by `ollama list`, including any `:latest` tag.

If host networking is required instead, set `network_mode: "host"` on the
backend, use `localhost` as the database host, and set
`OLLAMA_URL=http://localhost:11434`.

---

## Future work


- Update endpoint (`PUT /entries/{id}`) and pagination.
- Cross-entry theme analysis (find arcs across your whole journal).
- Streaming generation (token-by-token via SSE/WebSocket).
- Authentication & multi-user support.
- Vector embeddings + semantic search over entries.
- A richer frontend with timelines, mood charts and story galleries.
- Automated tests for services and the LLM-parsing fallback.

---

## License

MIT
