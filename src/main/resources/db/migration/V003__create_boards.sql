CREATE TABLE boards (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    owner_id INTEGER NOT NULL,
    name TEXT NOT NULL CHECK (length(trim(name)) BETWEEN 1 AND 100),
    created_on INTEGER NOT NULL DEFAULT (unixepoch()),
    FOREIGN KEY (owner_id) REFERENCES administrators(id) ON DELETE CASCADE
);
