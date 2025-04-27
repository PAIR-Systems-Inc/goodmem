#!/bin/bash

# Exit immediately if a command exits with a non-zero status.
set -e

# --- Configuration ---
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
OUTPUT_DIR="$PROJECT_ROOT/server/src/main/resources/certs"
CERT_FILE="$OUTPUT_DIR/server.crt"
KEY_FILE="$OUTPUT_DIR/server.key"

# Create the output directory if it doesn't exist
mkdir -p "$OUTPUT_DIR"
echo "Output directory: $OUTPUT_DIR"

# Distinguished Name components
DNAME_CN="localhost"
DNAME_OU="Development"       # Organizational Unit
DNAME_O="GoodMem"            # Organization
DNAME_L="LocalCity"          # Locality (City)
DNAME_ST="CA"                # State or Province
DNAME_C="US"                 # Country Code

# --- Script Logic ---
echo "Generating self-signed certificate for gRPC server"

# Create output directory if it doesn't exist
mkdir -p "$OUTPUT_DIR"

# Check if files already exist
if [ -f "$CERT_FILE" ] || [ -f "$KEY_FILE" ]; then
    echo "Certificate or key file already exists:"
    [ -f "$CERT_FILE" ] && echo "  - $CERT_FILE"
    [ -f "$KEY_FILE" ] && echo "  - $KEY_FILE"
    
    read -p "Do you want to overwrite them? (y/n): " confirm
    if [[ "$confirm" != [yY] ]]; then
        echo "Aborted. No files were modified."
        exit 0
    fi
fi

# Generate subject string
SUBJECT="/CN=$DNAME_CN/OU=$DNAME_OU/O=$DNAME_O/L=$DNAME_L/ST=$DNAME_ST/C=$DNAME_C"

echo "Generating RSA private key..."
openssl genrsa -out "$KEY_FILE" 2048

echo "Generating self-signed certificate..."
openssl req -new -x509 -key "$KEY_FILE" -out "$CERT_FILE" -days 3650 -subj "$SUBJECT" \
    -addext "subjectAltName = DNS:localhost,IP:127.0.0.1,IP:::1"

echo "---"
echo "Certificate generation complete."
echo "Certificate file: $CERT_FILE"
echo "Private key file: $KEY_FILE"
echo ""
echo "IMPORTANT:"
echo "1. These files are required for the gRPC server to use TLS."
echo "2. This is a SELF-SIGNED certificate. Clients (browsers, gRPC clients, etc.) will NOT trust it by default."
echo "3. The certificate is valid for 10 years (3650 days)."
echo "4. The certificate includes localhost and loopback IPs in the Subject Alternative Name (SAN) extension."
echo ""
echo "The server is already configured to use these certificate files."
echo "When you start the server, it should automatically detect and use these certificates."
echo ""
echo "For clients using the insecure flag:"
echo "CLI: goodmem --server=https://localhost:9090 --insecure ..."

chmod +x "$0"
exit 0