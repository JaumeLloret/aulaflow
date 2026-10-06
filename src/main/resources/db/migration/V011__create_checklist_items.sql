CREATE TABLE checklist_items (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    card_id INTEGER NOT NULL
        REFERENCES cards(id)
        ON DELETE CASCADE,
    text TEXT NOT NULL
        CHECK (length(trim(text)) BETWEEN 1 AND 280),
    completed INTEGER NOT NULL DEFAULT 0
        CHECK (completed IN (0, 1)),
    position INTEGER NOT NULL
        CHECK (position >= 0),
    created_on INTEGER NOT NULL DEFAULT (unixepoch()),
    updated_on INTEGER NOT NULL DEFAULT (unixepoch()),
    UNIQUE (card_id, position)
);
