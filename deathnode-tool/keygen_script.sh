#!/bin/bash
#
# generate-keys.sh - Generate all necessary keys for DeathNode nodes
#
# Usage: ./generate-keys.sh <node-name>
# Example: ./generate-keys.sh tomás

set -e

if [ $# -ne 1 ]; then
    echo "Usage: $0 <node-name>"
    echo "Example: $0 tomás"
    exit 1
fi

NODE=$1
KEYS_DIR="keys"

echo "=== Generating keys for node: $NODE ==="
echo

# Create keys directory if it doesn't exist
mkdir -p "$KEYS_DIR"

echo "1. Generating Ed25519 keys (for signing)..."

# Generate Ed25519 private key (PEM)
openssl genpkey -algorithm Ed25519 -out "${KEYS_DIR}/${NODE}_ed25519_priv.pem"

# Extract Ed25519 public key (PEM)
openssl pkey -in "${KEYS_DIR}/${NODE}_ed25519_priv.pem" -pubout \
    -out "${KEYS_DIR}/${NODE}_ed25519_pub.pem"

# Convert Ed25519 private key to DER (PKCS#8)
openssl pkcs8 -topk8 -inform PEM -outform DER \
    -in "${KEYS_DIR}/${NODE}_ed25519_priv.pem" \
    -out "${KEYS_DIR}/${NODE}_ed25519_priv.der" -nocrypt

# Convert Ed25519 public key to DER (X.509)
openssl pkey -pubin -inform PEM -outform DER \
    -in "${KEYS_DIR}/${NODE}_ed25519_pub.pem" \
    -out "${KEYS_DIR}/${NODE}_ed25519_pub.der"

echo "> Ed25519 keys generated!"
echo

echo "2. Generating RSA keys (for encryption)..."

# Generate RSA private key (PEM, 2048 bits)
openssl genrsa -out "${KEYS_DIR}/${NODE}_rsa_priv.pem" 2048

# Extract RSA public key (PEM)
openssl rsa -in "${KEYS_DIR}/${NODE}_rsa_priv.pem" -pubout \
    -out "${KEYS_DIR}/${NODE}_rsa_pub.pem"

# Convert RSA private key to DER (PKCS#8)
openssl pkcs8 -topk8 -inform PEM -outform DER \
    -in "${KEYS_DIR}/${NODE}_rsa_priv.pem" \
    -out "${KEYS_DIR}/${NODE}_rsa_priv.der" -nocrypt

# Convert RSA public key to DER (X.509)
openssl rsa -pubin -inform PEM -outform DER \
    -in "${KEYS_DIR}/${NODE}_rsa_pub.pem" \
    -out "${KEYS_DIR}/${NODE}_rsa_pub.der"

echo "> RSA keys generated!"
echo

echo "=== Key generation complete for $NODE ==="
echo
echo "Generated files in $KEYS_DIR/:"
echo "  ${NODE}_ed25519_priv.pem  (Ed25519 private key, PEM)"
echo "  ${NODE}_ed25519_pub.pem   (Ed25519 public key, PEM)"
echo "  ${NODE}_ed25519_priv.der  (Ed25519 private key, DER) <- Use this*"
echo "  ${NODE}_ed25519_pub.der   (Ed25519 public key, DER)  <- Use this*"
echo "  ${NODE}_rsa_priv.pem      (RSA private key, PEM)"
echo "  ${NODE}_rsa_pub.pem       (RSA public key, PEM)"
echo "  ${NODE}_rsa_priv.der      (RSA private key, DER)     <- Use this*"
echo "  ${NODE}_rsa_pub.der       (RSA public key, DER)      <- Use this*"
echo
echo "*Note: The Java tool requires DER format (.der files)"
echo

# Set appropriate permissions
chmod 600 "${KEYS_DIR}/${NODE}"_*_priv.*
chmod 644 "${KEYS_DIR}/${NODE}"_*_pub.*

echo "> File permissions set (private keys: 600, public keys: 644)"
