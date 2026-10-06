CREATE TABLE labels (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    board_id INTEGER NOT NULL
        REFERENCES boards(id)
        ON DELETE CASCADE,
    name TEXT NOT NULL
        CHECK (length(trim(name)) BETWEEN 1 AND 40),
    normalized_name TEXT NOT NULL
        CHECK (length(normalized_name) BETWEEN 1 AND 120),
    color TEXT NOT NULL
        CHECK (color IN (
            'red', 'orange', 'yellow', 'green',
            'blue', 'purple', 'gray'
        )),
    created_on INTEGER NOT NULL DEFAULT (unixepoch()),
    updated_on INTEGER NOT NULL DEFAULT (unixepoch()),
    UNIQUE (board_id, normalized_name)
);
