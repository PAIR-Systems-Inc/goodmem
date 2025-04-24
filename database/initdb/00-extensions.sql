-- Target file: database/initdb/00-extensions.sql
-- Enable extensions required by the schema.
-- The postgres container entrypoint executes scripts in /docker-entrypoint-initdb.d alphabetically.
CREATE EXTENSION IF NOT EXISTS "uuid-ossp"; -- For uuid_generate_v4()
CREATE EXTENSION IF NOT EXISTS vector;    -- For vector type and indexing
CREATE EXTENSION IF NOT EXISTS pgcrypto;  -- For additional cryptographic functions