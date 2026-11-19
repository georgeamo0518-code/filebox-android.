-- On-device schema (SQLite via Room), version 1.
-- Room generates the real DDL from @Entity classes; this file is the reference they must match.
-- Conventions:
--   ids: INTEGER PRIMARY KEY (rowid). Single device, no sync, so uuids buy nothing.
--   times: INTEGER epoch milliseconds, UTC. Formatted in the device zone only at display time.
--   location: either an absolute path ("/storage/emulated/0/Download/a.pdf")
--             or a SAF document URI ("content://com.android.externalstorage.documents/tree/...").
--   no owner column: one user, and the database lives in the app's private sandbox.
--   settings (theme, sort, view, show_hidden, use_trash...) live in DataStore, not here.

PRAGMA foreign_keys = ON;

-- S13 Favourites / bookmarks (F10)
CREATE TABLE favorites (
  id          INTEGER PRIMARY KEY,
  location    TEXT    NOT NULL,
  label       TEXT    NOT NULL,
  is_dir      INTEGER NOT NULL CHECK (is_dir IN (0, 1)),
  sort_order  INTEGER NOT NULL DEFAULT 0,
  created_at  INTEGER NOT NULL,
  updated_at  INTEGER NOT NULL
);
CREATE UNIQUE INDEX favorites_location_uq ON favorites (location);
CREATE INDEX favorites_sort_idx ON favorites (sort_order);

-- S15 Recycle bin (F06). The file sits in <volume root>/.<app>_trash/<id>_<name>.
CREATE TABLE trash_items (
  id             INTEGER PRIMARY KEY,
  original_path  TEXT    NOT NULL,           -- where Restore puts it back
  trash_path     TEXT    NOT NULL,           -- where it is now
  volume_id      TEXT    NOT NULL,           -- StorageVolume uuid or 'primary'
  name           TEXT    NOT NULL,
  is_dir         INTEGER NOT NULL CHECK (is_dir IN (0, 1)),
  size_bytes     INTEGER NOT NULL CHECK (size_bytes >= 0),
  deleted_at     INTEGER NOT NULL,
  created_at     INTEGER NOT NULL,
  updated_at     INTEGER NOT NULL
);
CREATE UNIQUE INDEX trash_items_trash_path_uq ON trash_items (trash_path);
CREATE INDEX trash_items_deleted_at_idx ON trash_items (deleted_at);   -- list order + 30-day purge
CREATE INDEX trash_items_volume_idx ON trash_items (volume_id);         -- volume ejected -> hide its rows

-- S14 Hidden files (F11). Hiding renames to a dot-prefixed name in place and records it here.
CREATE TABLE hidden_items (
  id             INTEGER PRIMARY KEY,
  original_path  TEXT    NOT NULL,
  hidden_path    TEXT    NOT NULL,
  is_dir         INTEGER NOT NULL CHECK (is_dir IN (0, 1)),
  hidden_at      INTEGER NOT NULL,
  created_at     INTEGER NOT NULL,
  updated_at     INTEGER NOT NULL
);
CREATE UNIQUE INDEX hidden_items_hidden_path_uq ON hidden_items (hidden_path);
CREATE INDEX hidden_items_hidden_at_idx ON hidden_items (hidden_at);

-- S06 Recent search queries (could-have)
CREATE TABLE search_queries (
  id           INTEGER PRIMARY KEY,
  text         TEXT    NOT NULL,
  filters      TEXT    NOT NULL DEFAULT '{}',   -- json: {"type":"docs","minSize":104857600,"after":...}
  searched_at  INTEGER NOT NULL,
  created_at   INTEGER NOT NULL,
  updated_at   INTEGER NOT NULL
);
CREATE UNIQUE INDEX search_queries_text_filters_uq ON search_queries (text, filters);  -- re-search = bump searched_at
CREATE INDEX search_queries_searched_at_idx ON search_queries (searched_at);

-- S09 File operations queue (F03 F04 F06). Survives process death so a running op can resume or report.
CREATE TABLE file_operations (
  id            INTEGER PRIMARY KEY,
  type          TEXT    NOT NULL CHECK (type IN ('copy','move','delete','trash','restore')),
  destination   TEXT,                                   -- null for delete/trash; for restore, unused (items carry targets)
  status        TEXT    NOT NULL DEFAULT 'queued'
                CHECK (status IN ('queued','running','conflict','done','failed','cancelled')),
  conflict_policy TEXT  CHECK (conflict_policy IN ('ask','skip','replace','keep_both')),
  bytes_total   INTEGER NOT NULL DEFAULT 0 CHECK (bytes_total >= 0),
  bytes_done    INTEGER NOT NULL DEFAULT 0 CHECK (bytes_done >= 0),
  items_total   INTEGER NOT NULL DEFAULT 0,
  items_done    INTEGER NOT NULL DEFAULT 0,
  error         TEXT,
  work_id       TEXT,                                   -- WorkManager request id
  created_at    INTEGER NOT NULL,
  updated_at    INTEGER NOT NULL,
  finished_at   INTEGER,
  CHECK (bytes_done <= bytes_total OR bytes_total = 0),
  CHECK (type IN ('delete','trash','restore') OR destination IS NOT NULL)
);
CREATE INDEX file_operations_status_idx ON file_operations (status, created_at);

-- One row per source item of an operation. Per-item state makes resume idempotent.
CREATE TABLE file_operation_items (
  id            INTEGER PRIMARY KEY,
  operation_id  INTEGER NOT NULL REFERENCES file_operations(id) ON DELETE CASCADE,
  source        TEXT    NOT NULL,
  target        TEXT,                                   -- resolved final path after conflict handling
  status        TEXT    NOT NULL DEFAULT 'pending'
                CHECK (status IN ('pending','done','skipped','failed')),
  error         TEXT,
  created_at    INTEGER NOT NULL,
  updated_at    INTEGER NOT NULL
);
CREATE INDEX file_operation_items_op_idx ON file_operation_items (operation_id, status);

-- Per-folder view and sort memory (could-have). Absent row = global default from DataStore.
CREATE TABLE folder_view_prefs (
  location    TEXT    PRIMARY KEY,
  view_mode   TEXT    NOT NULL CHECK (view_mode IN ('list','grid')),
  sort_field  TEXT    NOT NULL CHECK (sort_field IN ('name','date','size','type')),
  sort_dir    TEXT    NOT NULL CHECK (sort_dir IN ('asc','desc')),
  created_at  INTEGER NOT NULL,
  updated_at  INTEGER NOT NULL
);
