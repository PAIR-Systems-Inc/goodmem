# Target file: database/Dockerfile
FROM postgres:16-bookworm

# Install pg_vector extension compatible with the chosen PG version
RUN apt-get update && apt-get install -y --no-install-recommends \
    postgresql-16-pgvector \
 && rm -rf /var/lib/apt/lists/*

# Copy initialization scripts into the docker entrypoint directory.
# Scripts in this directory are executed automatically on first container start.
COPY ./initdb/ /docker-entrypoint-initdb.d/

# Standard PostgreSQL port
EXPOSE 5432