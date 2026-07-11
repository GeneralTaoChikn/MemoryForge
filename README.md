# MemoryForge 🔥📔

**MemoryForge** is a personal AI journal and story generator. Write down your
thoughts, dreams, gym progress, travel moments, career reflections or random
life updates — then let an LLM transform them into short stories, summaries,
recurring-theme analyses and character arcs. MemoryForge supports local models
via [Ollama](https://ollama.com) and hosted Google Gemini models via API key.

With Ollama, everything runs locally and privately: a Scala 3 / ZIO backend, a
PostgreSQL database, and your own model. With Gemini, journal content is sent to
Google's API for generation.

---

## ✨ Features

- Write & manage journal entries (title, content, mood, tags).
- Generate content from an entry in multiple **modes**:
  - `reflective_summary` – warm, reflective journal summary
  - `funny_story` – funny short story
  - `dramatic_story` – dramatic short story
  - `anime_scene` – anime-style scene
  - `brain_rot` – brain-rot meme version
  - `theme_analysis` – recurring theme analysis
- Structured JSON output from the LLM, with a graceful fallback to raw text when
  the model misbehaves.
- Clean architecture: `routes → service → repository → db`, plus dedicated
  `llm` provider-client and prompt-template layers.
- Robust error handling (missing entry, bad body, DB down, LLM down, timeout,
  invalid LLM response).
- Docker Compose for Postgres + backend (+ optional pgAdmin).

---

## 🧱 Tech stack

| Layer        | Technology                |
|--------------|---------------------------|
| Language     | Scala 3                   |
| Effects/HTTP | ZIO 2 + zio-http          |
| JSON         | zio-json                  |
| Database     | PostgreSQL (JDBC + HikariCP) |
| LLM          | Ollama (local) or Google Gemini |
| Packaging    | sbt-assembly + Docker     |

---

## 📂 Project structure

```
src/main/scala/memoryforge/
├── Main.scala                 # ZIO app bootstrap & layer wiring
├── config/AppConfig.scala     # env-var driven configuration
├── db/Database.scala          # Hikari pool + schema init + transact helper
├── domain/                    # models, errors, generation modes
│   ├── Models.scala
│   ├── GenerationMode.scala
│   └── AppError.scala
├── repository/                # JDBC persistence
│   ├── EntryRepository.scala
│   └── StoryRepository.scala
├── llm/                       # LLM provider clients + prompt templates
│   ├── LLMClient.scala
│   ├── GeminiClient.scala
│   ├── OllamaClient.scala
│   └── PromptTemplates.scala
├── service/                   # business logic
│   ├── EntryService.scala
│   └── StoryService.scala
└── routes/HttpApi.scala       # REST endpoints
```

---

## 🚀 Setup

### Prerequisites

- [Ollama](https://ollama.com) installed and running on the host, or a Google
  Gemini API key.
- A pulled model, e.g.:
  ```bash
  ollama pull llama3.1     # or: qwen2.5
  ```
- For local (non-Docker) runs: JDK 21, sbt, and a running PostgreSQL.

### Option A — Run everything with Docker Compose (recommended)

Ollama is assumed to run on your **host** machine (the backend reaches it via
`host.docker.internal`).

```bash
# 1. Make sure Ollama is running on the host and a model is pulled
ollama serve            # if not already running
ollama pull llama3.1

# 2. Build & start Postgres + backend
docker compose up --build

# (optional) also start pgAdmin at http://localhost:5050
docker compose --profile tools up --build
```

Backend will be available at <http://localhost:8080>.

### Option B — Run the backend locally with sbt

```bash
# Start only Postgres from compose (or use your own)
docker compose up -d db

# Configure via env vars (defaults shown)
export DB_URL=jdbc:postgresql://localhost:5432/memoryforge
export DB_USER=memoryforge
export DB_PASSWORD=memoryforge
export LLM_PROVIDER=ollama
export OLLAMA_URL=http://localhost:11434
export OLLAMA_MODEL=llama3.1

sbt run
```

To use Gemini instead:

```bash
export LLM_PROVIDER=gemini
export GEMINI_API_KEY=<your-api-key>
export GEMINI_MODEL=gemini-3.5-flash

sbt run
```

### Configuration (environment variables)

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

## 🔌 API

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

### Example API calls

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

Generate a funny story (the default mode):

```bash
curl -s -X POST http://localhost:8080/entries/<ENTRY_ID>/generate-story \
  -H "Content-Type: application/json" \
  -d '{ "mode": "funny_story" }'
```

Other modes work the same way — just change `"mode"`:
`reflective_summary`, `dramatic_story`, `anime_scene`, `brain_rot`,
`theme_analysis`. You can also override the configured provider's model:
`{ "mode": "anime_scene", "model": "qwen2.5" }` for Ollama or
`{ "mode": "anime_scene", "model": "gemini-3.5-flash" }` for Gemini.

Convenience endpoints:

```bash
curl -s -X POST http://localhost:8080/entries/<ENTRY_ID>/summarize
curl -s -X POST http://localhost:8080/entries/<ENTRY_ID>/analyze-themes
```

List everything:

```bash
curl -s http://localhost:8080/entries
curl -s http://localhost:8080/stories
```

### Example journal entry (response)

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

### Example generated story (response)

```json
{
  "id": "a1b2c3d4-0000-0000-0000-000000000001",
  "entryId": "0f7d2c2e-2c2e-4c2e-8c2e-2c2e2c2e2c2e",
  "title": "The Bar That Fought Back",
  "genre": "comedy",
  "style": "funny short story",
  "summary": "A heroic but underprepared lifter narrowly survives an epic battle with 100kg.",
  "story": "Our hero approached the iron throne of the squat rack with the confidence of a man who had absolutely not warmed up...",
  "createdAt": "2026-06-08T03:01:12Z"
}
```

---

## 🧠 How LLM output is handled

1. The prompt (see `PromptTemplates`) asks the configured LLM for a single JSON
   object with `title`, `genre`, `style`, `summary`, `story`.
2. `LLMClient` routes generation to `OllamaClient` or `GeminiClient`. Ollama
   uses `POST /api/generate` with `format: "json"`; Gemini uses the
   Interactions API with `response_format.mime_type: "application/json"`.
3. `StoryService.toStory` tries to parse that JSON. If parsing fails or the
   payload is empty, it **falls back** to storing the raw model text in the
   `story` field so nothing is ever lost.

---

## 🩺 Error handling

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

## 🖥️ Optional frontend

A zero-build single-file Vue app lives in `frontend/index.html`. The backend
already enables permissive CORS, so just serve the file and open it:

```bash
cd frontend
python3 -m http.server 5173
# then open http://localhost:5173
```

It lets you create entries, pick a generation mode, and view the forged stories.
If your backend isn't on `http://localhost:8080`, edit the `API` constant at the
top of the `<script>` block.

---

## 🧯 Troubleshooting: "Could not reach Ollama / Connection refused"

When the backend runs in Docker (e.g. WSL) and Ollama runs on the host, two
things must both be true:

**1. The backend must target `host.docker.internal`, not `localhost`.**
Inside a container, `localhost` is the *container itself*. The compose file
already sets `OLLAMA_URL=http://host.docker.internal:11434` plus the
`extra_hosts: host.docker.internal:host-gateway` mapping. If you changed it to
`localhost`, change it back (or set `OLLAMA_URL` in a `.env` file) and recreate:

```bash
docker compose up -d --build --force-recreate backend
```

**2. Ollama must listen on `0.0.0.0`, not just `127.0.0.1`.**
By default Ollama only binds loopback, so it refuses connections from the Docker
bridge even via `host.docker.internal`. On the WSL host, restart Ollama bound to
all interfaces:

```bash
# stop any running instance first, then:
OLLAMA_HOST=0.0.0.0:11434 ollama serve
```

(If Ollama runs as a systemd service: `systemctl edit ollama` and add
`Environment="OLLAMA_HOST=0.0.0.0:11434"`, then
`systemctl daemon-reload && systemctl restart ollama`.)

**Verify connectivity** from a throwaway container on the same Docker network:

```bash
docker run --rm --add-host=host.docker.internal:host-gateway curlimages/curl \
  -s http://host.docker.internal:11434/api/tags
```

You should get a JSON list of models. Also make sure the model is pulled and the
`OLLAMA_MODEL` value matches `ollama list` exactly (including the `:latest` tag).

> Tip: if you'd rather use host networking, uncomment nothing — instead set
> `network_mode: "host"` on the backend, change `DB_URL` host to `localhost`, and
> set `OLLAMA_URL=http://localhost:11434`. The `host.docker.internal` approach
> above is simpler and is what's configured by default.

---

## 🔭 Future improvements



- Update endpoint (`PUT /entries/{id}`) and pagination.
- Cross-entry theme analysis (find arcs across your whole journal).
- Streaming generation (token-by-token via SSE/WebSocket).
- Authentication & multi-user support.
- Vector embeddings + semantic search over entries.
- A richer frontend with timelines, mood charts and story galleries.
- Automated tests for services and the LLM-parsing fallback.

---

## 📜 License

MIT — personal project, hack away.
