# Build stage
FROM golang:1.22-alpine AS builder

WORKDIR /app

# Copy only go.mod and go.sum first to leverage Docker cache
COPY go.mod ./
# Download all dependencies
RUN go mod download

# Copy the source code
COPY . .

# Build the application with commit information
ARG GIT_COMMIT=dev
RUN CGO_ENABLED=0 GOOS=linux go build -ldflags "-X main.gitCommit=${GIT_COMMIT}" -o goodmem .

# Run tests
RUN go vet ./...
RUN go test ./...

# Final stage
FROM scratch

# Copy the binary from the builder stage
COPY --from=builder /app/goodmem /goodmem

# Metadata
LABEL org.opencontainers.image.source=https://github.com/pairsys/goodmem
LABEL org.opencontainers.image.description="GoodMem CLI"
LABEL org.opencontainers.image.licenses=Apache-2.0

# Run the binary
ENTRYPOINT ["/goodmem"]