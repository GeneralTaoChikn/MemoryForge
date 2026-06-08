-- MemoryForge database schema
-- This file is run automatically by the postgres container on first boot
-- (mounted into /docker-entrypoint-initdb.d). The backend also runs the same
-- CREATE TABLE IF NOT EXISTS statements on startup, so it is safe to run twice.

CREATE TABLE IF NOT EXISTS entries (
    id         UUID PRIMARY KEY,
    title      TEXT        NOT NULL,
    content    TEXT        NOT NULL,
    mood       TEXT,
    tags       TEXT, -- comma-separated list of tags
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS stories (
    id         UUID PRIMARY KEY,
    entry_id   UUID        NOT NULL REFERENCES entries(id) ON DELETE CASCADE,
    title      TEXT,
    genre      TEXT,
    style      TEXT,
    summary    TEXT,
    story      TEXT,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_stories_entry_id ON stories(entry_id);
CREATE INDEX IF NOT EXISTS idx_entries_created_at ON entries(created_at DESC);
