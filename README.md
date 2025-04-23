# GoodMem

Memory APIs with CLI and UI interfaces.

## Quick Start

### CLI Quick Start

#### Build Locally

```bash
# Using build script (recommended)
./cli/build.sh

# Or manually
cd cli && go build -o ../dist/goodmem .
../dist/goodmem version
```

#### Run with Docker

```bash
docker build -t goodmem-cli ./cli
docker run --rm goodmem-cli version
```