#!/bin/bash
#
# keygen-nodes.sh - Generate all necessary keys for DeathNode client nodes
#
# Creates keystore (JKS) with private keys for each node, and PEM files in public_keys directory
#
# Usage: ./keygen-nodes.sh <node-id-1> [<node-id-2> ...]
# Example: ./keygen-nodes.sh "1.1.1.1" "2.2.2.2" "3.3.3.3"

set -e

if [ $# -lt 1 ]; then
    echo "Usage: $0 <node-id-1> [<node-id-2> ...]"
    echo "Example: $0 \"1.1.1.1\" \"2.2.2.2\""
    echo ""
    echo "This script generates:"
    echo "  - Ed25519 and RSA key pairs (PEM format)"
    echo "  - Java Keystore (JKS) with private keys"
    echo "  - Public key PEM files in public_keys/"
    exit 1
fi

KEYSTORE_PASSWORD="demonstration"
PUBLIC_KEYS_DIR="public_keys"

# Create public_keys directory if it doesn't exist
mkdir -p "$PUBLIC_KEYS_DIR"

echo "=========================================="
echo "DeathNode Key Generation Tool"
echo "=========================================="
echo ""
echo "Generating keys for $# node(s)..."
echo ""

for NODE_ID in "$@"; do
    echo ">>> Processing node: $NODE_ID"
    
    NODE_KEYS_DIR="client-data/${NODE_ID}/keys"
    mkdir -p "$NODE_KEYS_DIR"
    
    TEMP_KEYS_DIR="/tmp/deathnode_${NODE_ID}_keys_$$"
    mkdir -p "$TEMP_KEYS_DIR"
    
    trap "rm -rf $TEMP_KEYS_DIR" EXIT
    
    echo "  1. Generating Ed25519 keys (signing)..."
    
    # Generate Ed25519 private key (PEM)
    openssl genpkey -algorithm Ed25519 -out "$TEMP_KEYS_DIR/${NODE_ID}_ed25519_priv.pem"
    
    # Extract Ed25519 public key (PEM) - goes to public_keys/
    openssl pkey -in "$TEMP_KEYS_DIR/${NODE_ID}_ed25519_priv.pem" -pubout \
        -out "$PUBLIC_KEYS_DIR/${NODE_ID}_ed25519_pub.pem"
    
    # Also keep copy in node directory (for reference)
    cp "$PUBLIC_KEYS_DIR/${NODE_ID}_ed25519_pub.pem" "$NODE_KEYS_DIR/${NODE_ID}_ed25519_pub.pem"
    
    # Convert Ed25519 private key to DER (PKCS#8)
    openssl pkcs8 -topk8 -inform PEM -outform DER \
        -in "$TEMP_KEYS_DIR/${NODE_ID}_ed25519_priv.pem" \
        -out "$NODE_KEYS_DIR/${NODE_ID}_ed25519_priv.der" -nocrypt
    
    echo "  2. Generating RSA keys (encryption)..."
    
    # Generate RSA private key (PEM, 2048 bits)
    openssl genrsa -out "$TEMP_KEYS_DIR/${NODE_ID}_rsa_priv.pem" 2048 2>/dev/null
    
    # Extract RSA public key (PEM) - goes to public_keys/
    openssl rsa -in "$TEMP_KEYS_DIR/${NODE_ID}_rsa_priv.pem" -pubout \
        -out "$PUBLIC_KEYS_DIR/${NODE_ID}_rsa_pub.pem"
    
    # Also keep copy in node directory (for reference)
    cp "$PUBLIC_KEYS_DIR/${NODE_ID}_rsa_pub.pem" "$NODE_KEYS_DIR/${NODE_ID}_rsa_pub.pem"
    
    # Convert RSA private key to DER (PKCS#8)
    openssl pkcs8 -topk8 -inform PEM -outform DER \
        -in "$TEMP_KEYS_DIR/${NODE_ID}_rsa_priv.pem" \
        -out "$NODE_KEYS_DIR/${NODE_ID}_rsa_priv.der" -nocrypt
    
    echo "  3. Creating Java Keystore (JKS)..."
    
    # Create temporary self-signed certificates
    openssl req -new -x509 -days 365 -key "$TEMP_KEYS_DIR/${NODE_ID}_ed25519_priv.pem" \
        -out "$TEMP_KEYS_DIR/${NODE_ID}_ed25519.crt" \
        -subj "/CN=node-${NODE_ID}" 2>/dev/null || true
    
    openssl req -new -x509 -days 365 -key "$TEMP_KEYS_DIR/${NODE_ID}_rsa_priv.pem" \
        -out "$TEMP_KEYS_DIR/${NODE_ID}_rsa.crt" \
        -subj "/CN=node-${NODE_ID}" 2>/dev/null || true
    
    # Convert PEM keys and certificates to PKCS12 for import
    openssl pkcs12 -export -in "$TEMP_KEYS_DIR/${NODE_ID}_ed25519.crt" \
        -inkey "$TEMP_KEYS_DIR/${NODE_ID}_ed25519_priv.pem" \
        -out "$TEMP_KEYS_DIR/${NODE_ID}_ed25519.p12" \
        -name "sign-key" -passout "pass:${KEYSTORE_PASSWORD}" 2>/dev/null || true
    
    openssl pkcs12 -export -in "$TEMP_KEYS_DIR/${NODE_ID}_rsa.crt" \
        -inkey "$TEMP_KEYS_DIR/${NODE_ID}_rsa_priv.pem" \
        -out "$TEMP_KEYS_DIR/${NODE_ID}_rsa.p12" \
        -name "rsa-key" -passout "pass:${KEYSTORE_PASSWORD}" 2>/dev/null || true
    
    # Create empty JKS keystore (with temp key)
    keytool -genkeypair -alias temp -keystore "$NODE_KEYS_DIR/keystore.jks" \
        -storepass "$KEYSTORE_PASSWORD" -keyalg RSA -keysize 2048 \
        -dname "CN=temp" -validity 365 -noprompt >/dev/null 2>&1
    
    # Import Ed25519 key
    keytool -importkeystore -srckeystore "$TEMP_KEYS_DIR/${NODE_ID}_ed25519.p12" \
        -srcstoretype PKCS12 -srcstorepass "$KEYSTORE_PASSWORD" \
        -destkeystore "$NODE_KEYS_DIR/keystore.jks" -deststoretype JKS \
        -deststorepass "$KEYSTORE_PASSWORD" -noprompt >/dev/null 2>&1 || true
    
    # Import RSA key
    keytool -importkeystore -srckeystore "$TEMP_KEYS_DIR/${NODE_ID}_rsa.p12" \
        -srcstoretype PKCS12 -srcstorepass "$KEYSTORE_PASSWORD" \
        -destkeystore "$NODE_KEYS_DIR/keystore.jks" -deststoretype JKS \
        -deststorepass "$KEYSTORE_PASSWORD" -noprompt >/dev/null 2>&1 || true
    
    # Delete temp key
    keytool -delete -alias temp -keystore "$NODE_KEYS_DIR/keystore.jks" \
        -storepass "$KEYSTORE_PASSWORD" -noprompt >/dev/null 2>&1
    
    # Set file permissions
    chmod 600 "$NODE_KEYS_DIR"/*.der "$NODE_KEYS_DIR"/*.jks
    chmod 644 "$NODE_KEYS_DIR"/*.pem "$PUBLIC_KEYS_DIR"/${NODE_ID}_*.pem
    
    echo "  ✓ Node $NODE_ID keys generated successfully"
    echo "    - Keystore: $NODE_KEYS_DIR/keystore.jks"
    echo "    - Private keys: $NODE_KEYS_DIR/*(priv.*)"
    echo "    - Public keys: $PUBLIC_KEYS_DIR/${NODE_ID}_*_pub.pem"
    echo ""
done

echo "=========================================="
echo "Key generation complete!"
echo "=========================================="
echo ""
echo "Generated structure:"
echo "  public_keys/"
for NODE_ID in "$@"; do
    echo "    ├── ${NODE_ID}_ed25519_pub.pem"
    echo "    └── ${NODE_ID}_rsa_pub.pem"
done
echo ""
for NODE_ID in "$@"; do
    echo "  client-data/${NODE_ID}/keys/"
    echo "    ├── keystore.jks"
    echo "    ├── ${NODE_ID}_ed25519_priv.der"
    echo "    ├── ${NODE_ID}_ed25519_pub.pem"
    echo "    ├── ${NODE_ID}_rsa_priv.der"
    echo "    └── ${NODE_ID}_rsa_pub.pem"
done
echo ""
echo "Next steps:"
echo "  1. Run the client with: java -jar target/deathnode-client-1.0.0.jar <node-id> [pseudonym]"
echo "  2. The keystore password is: $KEYSTORE_PASSWORD"
echo ""
