-- Target file: database/initdb/01-schema.sql

-- Table for Users
CREATE TABLE "user" ( -- Quoted because user is a reserved keyword in SQL
    user_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    username VARCHAR(100) UNIQUE,
    email VARCHAR(255) UNIQUE NOT NULL,
    display_name VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT current_timestamp,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT current_timestamp
);
CREATE INDEX idx_user_email ON "user" (email);

-- Table for Role definitions
CREATE TABLE role (
    role_name VARCHAR(16) PRIMARY KEY,
    description TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT current_timestamp,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT current_timestamp
);

-- Insert standard roles
INSERT INTO role (role_name, description) VALUES
    ('ROOT', 'System owner with full access to all resources and operations'),
    ('ADMIN', 'Administrator with broad access to system management functions'),
    ('USER', 'Standard user with access to own resources and basic system features');

-- Table for User-Role mappings
CREATE TABLE user_role (
    user_id UUID NOT NULL REFERENCES "user"(user_id) ON DELETE CASCADE,
    role_name VARCHAR(16) NOT NULL REFERENCES role(role_name) ON DELETE RESTRICT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT current_timestamp,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT current_timestamp,
    PRIMARY KEY (user_id, role_name)
);
CREATE INDEX idx_user_role_role_name ON user_role (role_name);

-- Table for API Keys
CREATE TABLE apikey (
    api_key_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES "user"(user_id) ON DELETE CASCADE,
    key_prefix VARCHAR(10) NOT NULL, -- For display purposes
    key_hash BYTEA NOT NULL UNIQUE, -- Store a hash of the key, not the raw key
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE', -- e.g., ACTIVE, INACTIVE (Consider ENUM type)
    labels JSONB,
    expires_at TIMESTAMPTZ,
    last_used_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT current_timestamp,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT current_timestamp,
    created_by_id UUID NOT NULL REFERENCES "user"(user_id),
    updated_by_id UUID NOT NULL REFERENCES "user"(user_id)
);
CREATE INDEX idx_apikey_user_id ON apikey (user_id);
CREATE INDEX idx_apikey_status ON apikey (status);
CREATE INDEX idx_apikey_created_by_id ON apikey (created_by_id);
CREATE INDEX idx_apikey_updated_by_id ON apikey (updated_by_id);

-- Table for Spaces
CREATE TABLE space (
    space_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    owner_id UUID NOT NULL REFERENCES "user"(user_id),
    name VARCHAR(255) NOT NULL,
    labels JSONB,
    embedding_model VARCHAR(100) NOT NULL,
    public_read BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT current_timestamp,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT current_timestamp,
    created_by_id UUID NOT NULL REFERENCES "user"(user_id),
    updated_by_id UUID NOT NULL REFERENCES "user"(user_id),
    UNIQUE (owner_id, name)
);
CREATE INDEX idx_space_owner_id ON space (owner_id);
CREATE INDEX idx_space_labels ON space USING GIN (labels);
CREATE INDEX idx_space_public_read ON space (public_read);
CREATE INDEX idx_space_created_by_id ON space (created_by_id);
CREATE INDEX idx_space_updated_by_id ON space (updated_by_id);
-- Add trigram index for name pattern matching with LIKE/ILIKE
CREATE INDEX idx_space_name_trgm ON space USING GIN (name gin_trgm_ops);

-- Table for Memories (Parent entity for chunks)
CREATE TABLE memory (
    memory_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    space_id UUID NOT NULL REFERENCES space(space_id) ON DELETE CASCADE,
    original_content_ref TEXT, -- Example: URI like s3://bucket/path/to/object
    content_type VARCHAR(100),
    metadata JSONB,
    processing_status VARCHAR(50) DEFAULT 'PENDING', -- Consider ENUM type
    created_at TIMESTAMPTZ NOT NULL DEFAULT current_timestamp,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT current_timestamp,
    created_by_id UUID NOT NULL REFERENCES "user"(user_id),
    updated_by_id UUID NOT NULL REFERENCES "user"(user_id)
);
CREATE INDEX idx_memory_space_id ON memory (space_id);
CREATE INDEX idx_memory_created_by_id ON memory (created_by_id);
CREATE INDEX idx_memory_updated_by_id ON memory (updated_by_id);
CREATE INDEX idx_memory_processing_status ON memory (processing_status);

-- Table for Memory Chunks (including vectors)
CREATE TABLE memory_chunk (
    chunk_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    memory_id UUID NOT NULL REFERENCES memory(memory_id) ON DELETE CASCADE,
    chunk_sequence_number INT,
    chunk_text TEXT, -- Storing the chunk text alongside the vector is often useful
    embedding_vector vector(1536), -- The vector embedding, 1536 dimensions
    vector_status VARCHAR(50) DEFAULT 'PENDING', -- Consider ENUM type
    start_offset INT,
    end_offset INT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT current_timestamp,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT current_timestamp,
    created_by_id UUID NOT NULL REFERENCES "user"(user_id),
    updated_by_id UUID NOT NULL REFERENCES "user"(user_id)
);
CREATE INDEX idx_memory_chunk_memory_id ON memory_chunk (memory_id);
CREATE INDEX idx_memory_chunk_vector_status ON memory_chunk (vector_status);
CREATE INDEX idx_memory_chunk_created_by_id ON memory_chunk (created_by_id);
CREATE INDEX idx_memory_chunk_updated_by_id ON memory_chunk (updated_by_id);
-- Vector index (HNSW using L2 distance - Euclidean)
CREATE INDEX idx_memory_chunk_embedding_vector ON memory_chunk USING hnsw (embedding_vector vector_l2_ops);
-- Alternative index using Cosine distance (commented out):
-- CREATE INDEX idx_memory_chunk_embedding_vector_cosine ON memory_chunk USING hnsw (embedding_vector vector_cosine_ops);

-- Trigger function to update 'updated_at' timestamps automatically
CREATE OR REPLACE FUNCTION trigger_set_timestamp()
RETURNS TRIGGER AS $$
BEGIN
  NEW.updated_at = NOW();
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Apply trigger to tables
CREATE TRIGGER set_timestamp_user BEFORE UPDATE ON "user" FOR EACH ROW EXECUTE FUNCTION trigger_set_timestamp();
CREATE TRIGGER set_timestamp_role BEFORE UPDATE ON role FOR EACH ROW EXECUTE FUNCTION trigger_set_timestamp();
CREATE TRIGGER set_timestamp_user_role BEFORE UPDATE ON user_role FOR EACH ROW EXECUTE FUNCTION trigger_set_timestamp();
CREATE TRIGGER set_timestamp_apikey BEFORE UPDATE ON apikey FOR EACH ROW EXECUTE FUNCTION trigger_set_timestamp();
CREATE TRIGGER set_timestamp_space BEFORE UPDATE ON space FOR EACH ROW EXECUTE FUNCTION trigger_set_timestamp();
CREATE TRIGGER set_timestamp_memory BEFORE UPDATE ON memory FOR EACH ROW EXECUTE FUNCTION trigger_set_timestamp();
CREATE TRIGGER set_timestamp_memory_chunk BEFORE UPDATE ON memory_chunk FOR EACH ROW EXECUTE FUNCTION trigger_set_timestamp();
CREATE TRIGGER set_timestamp_embedder BEFORE UPDATE ON embedder FOR EACH ROW EXECUTE FUNCTION trigger_set_timestamp();

-- ENUM types definitions
-- CREATE TYPE api_key_status AS ENUM ('ACTIVE', 'INACTIVE');
-- CREATE TYPE processing_status_enum AS ENUM ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED');
-- CREATE TYPE vector_status_enum AS ENUM ('PENDING', 'GENERATED', 'FAILED');

-- ENUM for modality types
CREATE TYPE modality_enum AS ENUM (
  'TEXT',
  'IMAGE',
  'AUDIO',
  'VIDEO'
);

-- ENUM for embedder provider types
CREATE TYPE provider_type_enum AS ENUM (
  'OPENAI',
  'VLLM',
  'TEI'          -- covers both native and OpenAI-compatible TEI endpoints
);

-- Table for Embedders
CREATE TABLE embedder (
    embedder_id            UUID PRIMARY KEY DEFAULT uuid_generate_v4(),

    /* user-facing */
    display_name           VARCHAR(255) NOT NULL,
    description            TEXT,

    /* connection details */
    provider_type          provider_type_enum NOT NULL,
    endpoint_url           TEXT NOT NULL,                    -- e.g. https://api.openai.com
    api_path               TEXT NOT NULL DEFAULT '/v1/embeddings',
    model_identifier       TEXT NOT NULL,                    -- e.g. text-embedding-3-small
    dimensionality         INT NOT NULL CHECK (dimensionality > 0), -- output vector size
    max_sequence_length    INT,
    supported_modalities   modality_enum[] NOT NULL DEFAULT ARRAY['TEXT']::modality_enum[],

    /* credentials - will eventually be encrypted */
    credentials            TEXT,

    /* labels & MLOps info */
    labels                 JSONB,
    version                VARCHAR(64),
    deployment_context     JSONB,
    monitoring_endpoint    TEXT,

    /* ownership & audit */
    owner_id               UUID NOT NULL REFERENCES "user"(user_id),
    created_at             TIMESTAMPTZ NOT NULL DEFAULT current_timestamp,
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT current_timestamp,
    created_by_id          UUID NOT NULL REFERENCES "user"(user_id),
    updated_by_id          UUID NOT NULL REFERENCES "user"(user_id),

    /* avoid duplicate registrations */
    UNIQUE (endpoint_url, api_path, model_identifier)
);

-- Indexes for the embedder table
CREATE INDEX idx_embedder_provider_type ON embedder (provider_type);
CREATE INDEX idx_embedder_owner_id ON embedder (owner_id);
CREATE INDEX idx_embedder_labels ON embedder USING GIN (labels);
CREATE INDEX idx_embedder_created_by_id ON embedder (created_by_id);
CREATE INDEX idx_embedder_updated_by_id ON embedder (updated_by_id);