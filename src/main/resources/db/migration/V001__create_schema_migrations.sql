CREATE TABLE schema_migrations (
    version TEXT PRIMARY KEY,
    description TEXT NOT NULL,
    installed_on TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);
