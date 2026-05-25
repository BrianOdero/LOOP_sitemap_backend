-- ============================================================
-- LOOP Platform — Database Schema
-- PostgreSQL 16
-- Generated: May 2026
-- ============================================================

-- ── ENUMS ────────────────────────────────────────────────────

CREATE TYPE user_role AS ENUM ('USER', 'ADMIN');

CREATE TYPE market_type AS ENUM ('FULL_PRODUCT', 'PARTNERSHIP');

CREATE TYPE feature_status AS ENUM (
    'NEW',
    'EXISTS',
    'GAP',
    'CONFLICT',
    'DEPENDENCY'
);

-- ── USERS ────────────────────────────────────────────────────

CREATE TABLE users (
    id              BIGSERIAL       PRIMARY KEY,
    email           VARCHAR(255)    NOT NULL UNIQUE,
    password_hash   VARCHAR(255)    NOT NULL,
    role            user_role       NOT NULL DEFAULT 'USER',
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW()
);

-- ── MARKETS ──────────────────────────────────────────────────

CREATE TABLE markets (
    id              BIGSERIAL       PRIMARY KEY,
    name            VARCHAR(100)    NOT NULL,
    code            VARCHAR(10)     NOT NULL UNIQUE,   -- e.g. KE, UG, TZ
    market_type     market_type     NOT NULL
);

-- ── SEGMENTS ─────────────────────────────────────────────────

CREATE TABLE segments (
    id              BIGSERIAL       PRIMARY KEY,
    name            VARCHAR(100)    NOT NULL,
    code            VARCHAR(50)     NOT NULL UNIQUE    -- e.g. CONSUMER, MERCHANT, PAAS
);

-- ── THEMES ───────────────────────────────────────────────────

CREATE TABLE themes (
    id              BIGSERIAL       PRIMARY KEY,
    name            VARCHAR(255)    NOT NULL,
    description     TEXT,
    segment_id      BIGINT          NOT NULL REFERENCES segments(id) ON DELETE CASCADE,
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_themes_segment_id ON themes(segment_id);

-- ── EPICS ────────────────────────────────────────────────────

CREATE TABLE epics (
    id              BIGSERIAL       PRIMARY KEY,
    name            VARCHAR(255)    NOT NULL,
    description     TEXT,
    theme_id        BIGINT          NOT NULL REFERENCES themes(id) ON DELETE CASCADE,
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_epics_theme_id ON epics(theme_id);

-- ── FEATURES ─────────────────────────────────────────────────

CREATE TABLE features (
    id              BIGSERIAL       PRIMARY KEY,
    name            VARCHAR(255)    NOT NULL,
    description     TEXT,
    status          feature_status  NOT NULL DEFAULT 'NEW',
    epic_id         BIGINT          NOT NULL REFERENCES epics(id) ON DELETE CASCADE,
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_features_epic_id ON features(epic_id);
CREATE INDEX idx_features_status  ON features(status);

-- ── USER STORIES ─────────────────────────────────────────────

CREATE TABLE user_stories (
    id                    BIGSERIAL   PRIMARY KEY,
    title                 VARCHAR(500) NOT NULL,
    description           TEXT,
    acceptance_criteria   TEXT,
    feature_id            BIGINT       NOT NULL REFERENCES features(id) ON DELETE CASCADE,
    created_at            TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_user_stories_feature_id ON user_stories(feature_id);

-- ── FEATURE ↔ MARKET (M2M) ───────────────────────────────────

CREATE TABLE feature_markets (
    feature_id  BIGINT  NOT NULL REFERENCES features(id)  ON DELETE CASCADE,
    market_id   BIGINT  NOT NULL REFERENCES markets(id)   ON DELETE CASCADE,
    PRIMARY KEY (feature_id, market_id)
);

CREATE INDEX idx_feature_markets_market_id ON feature_markets(market_id);

-- ── USER STORY ↔ MARKET (M2M) ────────────────────────────────

CREATE TABLE user_story_markets (
    user_story_id   BIGINT  NOT NULL REFERENCES user_stories(id) ON DELETE CASCADE,
    market_id       BIGINT  NOT NULL REFERENCES markets(id)      ON DELETE CASCADE,
    PRIMARY KEY (user_story_id, market_id)
);

CREATE INDEX idx_user_story_markets_market_id ON user_story_markets(market_id);

-- ── APP SETTINGS ─────────────────────────────────────────────
-- Key-value store for runtime config (e.g. encrypted Claude API key).
-- setting_key is unique — upsert by key to update in place.

CREATE TABLE app_settings (
    id              BIGSERIAL       PRIMARY KEY,
    setting_key     VARCHAR(100)    NOT NULL UNIQUE,
    setting_value   TEXT            NOT NULL,
    updated_at      TIMESTAMP       NOT NULL DEFAULT NOW()
);

-- ── SEED DATA ────────────────────────────────────────────────
-- Minimal bootstrap rows. Full feature/story data is inserted
-- by DataSeeder.java on first application startup.

INSERT INTO markets (name, code, market_type) VALUES
    ('Kenya',       'KE', 'FULL_PRODUCT'),
    ('Uganda',      'UG', 'PARTNERSHIP'),
    ('Tanzania',    'TZ', 'PARTNERSHIP'),
    ('Rwanda',      'RW', 'PARTNERSHIP'),
    ('Ghana',       'GH', 'PARTNERSHIP'),
    ('Ivory Coast', 'CI', 'PARTNERSHIP');

INSERT INTO segments (name, code) VALUES
    ('Consumer', 'CONSUMER'),
    ('Merchant', 'MERCHANT'),
    ('PaaS',     'PAAS');

-- Default admin user (password: Loop@Admin1 — BCrypt hash below)
-- Replace with a fresh hash before deploying to any non-local environment.
INSERT INTO users (email, password_hash, role) VALUES
    ('admin@loop.com',
     '$2a$12$pYourBCryptHashHereReplaceWithRealHash',
     'ADMIN');
