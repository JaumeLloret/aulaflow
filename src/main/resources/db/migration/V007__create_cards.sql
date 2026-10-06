CREATE TABLE cards (
    id INTEGER PRIMARY KEY,
    column_id INTEGER NOT NULL
        REFERENCES board_columns(id)
        ON DELETE CASCADE,
    title TEXT NOT NULL
        CHECK (length(trim(title)) BETWEEN 1 AND 160),
    description TEXT NOT NULL DEFAULT ''
        CHECK (length(description) <= 4000),
    position INTEGER NOT NULL
        CHECK (position >= 0),
    created_on INTEGER NOT NULL DEFAULT (unixepoch()),
    updated_on INTEGER NOT NULL DEFAULT (unixepoch()),
    UNIQUE (column_id, position)
)
