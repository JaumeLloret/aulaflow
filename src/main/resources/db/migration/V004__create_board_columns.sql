CREATE TABLE board_columns (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    board_id INTEGER NOT NULL,
    name TEXT NOT NULL CHECK (length(trim(name)) BETWEEN 1 AND 80),
    position INTEGER NOT NULL CHECK (position >= 0),
    created_on INTEGER NOT NULL DEFAULT (unixepoch()),
    FOREIGN KEY (board_id) REFERENCES boards(id) ON DELETE CASCADE,
    UNIQUE (board_id, position)
);
