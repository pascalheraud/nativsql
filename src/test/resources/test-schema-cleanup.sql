-- Data cleanup - executed before each test
-- This removes test data but preserves the schema and types
-- Explicit order to handle foreign key dependencies

TRUNCATE TABLE contact_info;
TRUNCATE TABLE users;
