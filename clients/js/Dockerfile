# Build stage for JavaScript client
FROM node:20-alpine

WORKDIR /app

# Copy package.json and lock files first to leverage Docker cache
COPY package.json package-lock.json* ./

# Copy test configuration
COPY .mocharc.json ./

# Copy source code and tests
COPY src/ ./src/
COPY test/ ./test/

# Install dependencies
RUN npm install

# Run tests
RUN npm test

# Package the client (optional if using npm pack)
RUN npm pack

# The output will be in /app/*.tgz
