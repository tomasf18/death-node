#!/bin/bash
set -e

if [ $# -lt 1 ]; then
    echo "Usage: $0 <entity-type> [node-name]"
    echo ""
    echo "Entity types:"
    echo "  ca                    - Generate Certificate Authority"
    echo "  client <node-name>    - Generate keys for a client node"
    echo "  server                - Generate keys for the server"
    echo "  database              - Generate keys for the database"
    echo ""
    echo "Examples:"
    echo "  $0 ca"
    echo "  $0 client nodeA"
    echo "  $0 server"
    echo "  $0 database"
    exit 1
fi

ENTITY_TYPE=$1
NODE_NAME=$2

# Colors for output
GREEN='\033[0;32m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# ============================================================================
# Generate Certificate Authority
# ============================================================================
generate_ca() {
    echo -e "${BLUE}=== Generating Certificate Authority ===${NC}"
    
    CA_DIR="ca"
    mkdir -p "$CA_DIR"
    
    # Generate CA private key (RSA 4096 for CA)
    echo "1. Generating CA private key..."
    openssl genrsa -out "${CA_DIR}/ca-key.pem" 4096
    
    # Generate CA self-signed certificate (valid for 1 year)
    echo "2. Generating CA certificate..."
    openssl req -new -x509 -days 365 -key "${CA_DIR}/ca-key.pem" \
        -out "${CA_DIR}/ca-cert.pem" \
        -subj "/C=PT/ST=Lisbon/L=Lisbon/O=DeathNode/OU=Security/CN=DeathNode-CA"
    
    # Set permissions
    chmod 600 "${CA_DIR}/ca-key.pem"
    chmod 644 "${CA_DIR}/ca-cert.pem"
    
    echo -e "${GREEN}> CA generated successfully!${NC}"
    echo "  CA Key: ${CA_DIR}/ca-key.pem"
    echo "  CA Cert: ${CA_DIR}/ca-cert.pem"
    echo ""
    echo "!! IMPORTANT: Keep ca-key.pem secure and never distribute it!"
    echo "   Only distribute ca-cert.pem to all nodes."
}

# ============================================================================
# Generate keys for CLIENT node
# ============================================================================
generate_client_keys() {
    if [ -z "$NODE_NAME" ]; then
        echo "Error: Node name required for client"
        echo "Usage: $0 client <node-name>"
        exit 1
    fi
    
    # Map node names to their static IPs (from boot-config*.sh)
    case "$NODE_NAME" in
        nodeA)
            NODE_IP="192.168.1.100"
            ;;
        nodeB)
            NODE_IP="192.168.2.100"
            ;;
        *)
            echo "Error: Unknown node '$NODE_NAME'"
            echo "Supported nodes: nodeA, nodeB"
            exit 1
            ;;
    esac
    
    echo -e "${BLUE}=== Generating keys for CLIENT: $NODE_NAME (IP: $NODE_IP) ===${NC}"
    
    # Create directory structure
    KEYS_DIR="deathnode-client/client-data/${NODE_NAME}/keys"
    mkdir -p "$KEYS_DIR"
    
    # 1. Ed25519 keys (signing - user level)
    echo -e "${GREEN}1. Generating Ed25519 keys (user-level signing)...${NC}"
    openssl genpkey -algorithm Ed25519 -out "${KEYS_DIR}/${NODE_NAME}_ed25519_priv.pem"
    openssl pkey -in "${KEYS_DIR}/${NODE_NAME}_ed25519_priv.pem" -pubout \
        -out "${KEYS_DIR}/${NODE_NAME}_ed25519_pub.pem"
    openssl pkcs8 -topk8 -inform PEM -outform DER \
        -in "${KEYS_DIR}/${NODE_NAME}_ed25519_priv.pem" \
        -out "${KEYS_DIR}/${NODE_NAME}_ed25519_priv.der" -nocrypt
    openssl pkey -pubin -inform PEM -outform DER \
        -in "${KEYS_DIR}/${NODE_NAME}_ed25519_pub.pem" \
        -out "${KEYS_DIR}/${NODE_NAME}_ed25519_pub.der"
    
    # 2. RSA keys (encryption - user level)
    echo -e "${GREEN}2. Generating RSA keys (user-level encryption)...${NC}"
    openssl genrsa -out "${KEYS_DIR}/${NODE_NAME}_rsa_priv.pem" 2048
    openssl rsa -in "${KEYS_DIR}/${NODE_NAME}_rsa_priv.pem" -pubout \
        -out "${KEYS_DIR}/${NODE_NAME}_rsa_pub.pem"
    openssl pkcs8 -topk8 -inform PEM -outform DER \
        -in "${KEYS_DIR}/${NODE_NAME}_rsa_priv.pem" \
        -out "${KEYS_DIR}/${NODE_NAME}_rsa_priv.der" -nocrypt
    openssl rsa -pubin -inform PEM -outform DER \
        -in "${KEYS_DIR}/${NODE_NAME}_rsa_pub.pem" \
        -out "${KEYS_DIR}/${NODE_NAME}_rsa_pub.der"
    
    # 3. TLS keys and certificate (gRPC - application level)
    echo -e "${GREEN}3. Generating TLS certificate (application-level gRPC)...${NC}"
    
    # Generate TLS private key
    openssl genrsa -out "${KEYS_DIR}/tls-key.pem" 2048
    
    # Create Certificate Signing Request (CSR)
    openssl req -new -key "${KEYS_DIR}/tls-key.pem" \
        -out "${KEYS_DIR}/tls-csr.pem" \
        -subj "/C=PT/ST=Lisbon/L=Lisbon/O=DeathNode/OU=Client/CN=deathnode-${NODE_NAME}"
    
    # Sign with CA (requires ca/ca-key.pem and ca/ca-cert.pem)
    if [ ! -f "ca/ca-key.pem" ] || [ ! -f "ca/ca-cert.pem" ]; then
        echo "Error: CA not found. Run '$0 ca' first"
        exit 1
    fi
    
    openssl x509 -req -in "${KEYS_DIR}/tls-csr.pem" \
        -CA "ca/ca-cert.pem" -CAkey "ca/ca-key.pem" -CAcreateserial \
        -out "${KEYS_DIR}/tls-cert.pem" -days 365 \
        -extfile <(printf "subjectAltName=DNS:deathnode-${NODE_NAME},IP:127.0.0.1,IP:${NODE_IP}")
    
    # Copy CA cert for trust
    cp "ca/ca-cert.pem" "${KEYS_DIR}/ca-cert.pem"
    
    # Clean up CSR
    rm "${KEYS_DIR}/tls-csr.pem"
    
    # 4. Create Java Keystore (for private keys)
    echo -e "${GREEN}4. Creating Java Keystore...${NC}"
    
    # Convert keys to PKCS12 for import into JKS
    # Create temporary self-signed certificates for Ed25519
    openssl req -new -x509 -days 365 -key "${KEYS_DIR}/${NODE_NAME}_ed25519_priv.pem" \
        -out /tmp/ed25519_temp.crt -subj "/CN=deathnode-${NODE_NAME}" 2>/dev/null
    
    # Create PKCS12 from Ed25519 key and certificate
    openssl pkcs12 -export -in /tmp/ed25519_temp.crt \
        -inkey "${KEYS_DIR}/${NODE_NAME}_ed25519_priv.pem" \
        -out /tmp/ed25519_temp.p12 -name "sign-key" \
        -passout pass:demonstration 2>/dev/null
    
    # Create temporary self-signed certificate for RSA
    openssl req -new -x509 -days 365 -key "${KEYS_DIR}/${NODE_NAME}_rsa_priv.pem" \
        -out /tmp/rsa_temp.crt -subj "/CN=deathnode-${NODE_NAME}" 2>/dev/null
    
    # Create PKCS12 from RSA key and certificate
    openssl pkcs12 -export -in /tmp/rsa_temp.crt \
        -inkey "${KEYS_DIR}/${NODE_NAME}_rsa_priv.pem" \
        -out /tmp/rsa_temp.p12 -name "rsa-key" \
        -passout pass:demonstration 2>/dev/null
    
    # Create empty JKS keystore with a temporary key
    keytool -genkeypair -alias temp -keystore "${KEYS_DIR}/keystore.jks" \
        -storepass demonstration -keyalg RSA -keysize 2048 \
        -dname "CN=temp" -validity 365 -noprompt >/dev/null 2>&1
    
    # Import Ed25519 PKCS12 into JKS
    keytool -importkeystore -srckeystore /tmp/ed25519_temp.p12 \
        -srcstoretype PKCS12 -srcstorepass demonstration \
        -destkeystore "${KEYS_DIR}/keystore.jks" -deststoretype JKS \
        -deststorepass demonstration -noprompt >/dev/null 2>&1 || true
    
    # Import RSA PKCS12 into JKS
    keytool -importkeystore -srckeystore /tmp/rsa_temp.p12 \
        -srcstoretype PKCS12 -srcstorepass demonstration \
        -destkeystore "${KEYS_DIR}/keystore.jks" -deststoretype JKS \
        -deststorepass demonstration -noprompt >/dev/null 2>&1 || true
    
    # Delete the temporary key
    keytool -delete -alias temp -keystore "${KEYS_DIR}/keystore.jks" \
        -storepass demonstration -noprompt >/dev/null 2>&1
    
    # Clean up temporary files
    rm -f /tmp/ed25519_temp.* /tmp/rsa_temp.*
    
    # Set permissions
    chmod 600 "${KEYS_DIR}"/*_priv.* "${KEYS_DIR}/tls-key.pem" "${KEYS_DIR}/keystore.jks"
    chmod 644 "${KEYS_DIR}"/*_pub.* "${KEYS_DIR}/tls-cert.pem" "${KEYS_DIR}/ca-cert.pem"
    
    echo -e "${GREEN}> Client keys generated successfully for ${NODE_NAME}!${NC}"
    echo "  Location: ${KEYS_DIR}/"
    echo "  IP Address: ${NODE_IP}"
}

# ============================================================================
# Generate keys for SERVER
# ============================================================================
generate_server_keys() {
    echo -e "${BLUE}=== Generating keys for SERVER ===${NC}"
    
    KEYS_DIR="deathnode-server/server-data/keys"
    mkdir -p "$KEYS_DIR"
    
    NODE_NAME="server"
    
    # 1. Ed25519 keys (signing - user level)
    echo -e "${GREEN}1. Generating Ed25519 keys (user-level signing)...${NC}"
    openssl genpkey -algorithm Ed25519 -out "${KEYS_DIR}/server_ed25519_priv.pem"
    openssl pkey -in "${KEYS_DIR}/server_ed25519_priv.pem" -pubout \
        -out "${KEYS_DIR}/server_ed25519_pub.pem"
    openssl pkcs8 -topk8 -inform PEM -outform DER \
        -in "${KEYS_DIR}/server_ed25519_priv.pem" \
        -out "${KEYS_DIR}/server_ed25519_priv.der" -nocrypt
    openssl pkey -pubin -inform PEM -outform DER \
        -in "${KEYS_DIR}/server_ed25519_pub.pem" \
        -out "${KEYS_DIR}/server_ed25519_pub.der"
    
    # 2. RSA keys (encryption - user level)
    echo -e "${GREEN}2. Generating RSA keys (user-level encryption)...${NC}"
    openssl genrsa -out "${KEYS_DIR}/server_rsa_priv.pem" 2048
    openssl rsa -in "${KEYS_DIR}/server_rsa_priv.pem" -pubout \
        -out "${KEYS_DIR}/server_rsa_pub.pem"
    openssl pkcs8 -topk8 -inform PEM -outform DER \
        -in "${KEYS_DIR}/server_rsa_priv.pem" \
        -out "${KEYS_DIR}/server_rsa_priv.der" -nocrypt
    openssl rsa -pubin -inform PEM -outform DER \
        -in "${KEYS_DIR}/server_rsa_pub.pem" \
        -out "${KEYS_DIR}/server_rsa_pub.der"
    
    # 3. TLS keys and certificate (gRPC)
    echo -e "${GREEN}3. Generating TLS certificate (application-level)...${NC}"
    
    openssl genrsa -out "${KEYS_DIR}/tls-key.pem" 2048
    
    openssl req -new -key "${KEYS_DIR}/tls-key.pem" \
        -out "${KEYS_DIR}/tls-csr.pem" \
        -subj "/C=PT/ST=Lisbon/L=Lisbon/O=DeathNode/OU=Server/CN=deathnode-server"
    
    if [ ! -f "ca/ca-key.pem" ] || [ ! -f "ca/ca-cert.pem" ]; then
        echo "Error: CA not found. Run '$0 ca' first"
        exit 1
    fi
    
    # Server cert with multiple SANs (for localhost, docker, VMs, etc.)
    openssl x509 -req -in "${KEYS_DIR}/tls-csr.pem" \
        -CA "ca/ca-cert.pem" -CAkey "ca/ca-key.pem" -CAcreateserial \
        -out "${KEYS_DIR}/tls-cert.pem" -days 365 \
        -extfile <(printf "subjectAltName=DNS:deathnode-server,IP:127.0.0.1,IP:192.168.0.10")
    
    cp "ca/ca-cert.pem" "${KEYS_DIR}/ca-cert.pem"
    rm "${KEYS_DIR}/tls-csr.pem"
    
    # 4. Create Java Keystore
    echo -e "${GREEN}4. Creating Java Keystore...${NC}"
    
    # Convert keys to PKCS12 for import into JKS
    # Create temporary self-signed certificates for Ed25519
    openssl req -new -x509 -days 365 -key "${KEYS_DIR}/server_ed25519_priv.pem" \
        -out /tmp/ed25519_temp.crt -subj "/CN=deathnode-server" 2>/dev/null
    
    # Create PKCS12 from Ed25519 key and certificate
    openssl pkcs12 -export -in /tmp/ed25519_temp.crt \
        -inkey "${KEYS_DIR}/server_ed25519_priv.pem" \
        -out /tmp/ed25519_temp.p12 -name "sign-key" \
        -passout pass:demonstration 2>/dev/null
    
    # Create temporary self-signed certificate for RSA
    openssl req -new -x509 -days 365 -key "${KEYS_DIR}/server_rsa_priv.pem" \
        -out /tmp/rsa_temp.crt -subj "/CN=deathnode-server" 2>/dev/null
    
    # Create PKCS12 from RSA key and certificate
    openssl pkcs12 -export -in /tmp/rsa_temp.crt \
        -inkey "${KEYS_DIR}/server_rsa_priv.pem" \
        -out /tmp/rsa_temp.p12 -name "rsa-key" \
        -passout pass:demonstration 2>/dev/null
    
    # Create empty JKS keystore with a temporary key
    keytool -genkeypair -alias temp -keystore "${KEYS_DIR}/keystore.jks" \
        -storepass demonstration -keyalg RSA -keysize 2048 \
        -dname "CN=temp" -validity 365 -noprompt >/dev/null 2>&1
    
    # Import Ed25519 PKCS12 into JKS
    keytool -importkeystore -srckeystore /tmp/ed25519_temp.p12 \
        -srcstoretype PKCS12 -srcstorepass demonstration \
        -destkeystore "${KEYS_DIR}/keystore.jks" -deststoretype JKS \
        -deststorepass demonstration -noprompt >/dev/null 2>&1 || true
    
    # Import RSA PKCS12 into JKS
    keytool -importkeystore -srckeystore /tmp/rsa_temp.p12 \
        -srcstoretype PKCS12 -srcstorepass demonstration \
        -destkeystore "${KEYS_DIR}/keystore.jks" -deststoretype JKS \
        -deststorepass demonstration -noprompt >/dev/null 2>&1 || true
    
    # Delete the temporary key
    keytool -delete -alias temp -keystore "${KEYS_DIR}/keystore.jks" \
        -storepass demonstration -noprompt >/dev/null 2>&1
    
    # Clean up temporary files
    rm -f /tmp/ed25519_temp.* /tmp/rsa_temp.*
    
    chmod 600 "${KEYS_DIR}"/*_priv.* "${KEYS_DIR}/tls-key.pem" "${KEYS_DIR}/keystore.jks"
    chmod 644 "${KEYS_DIR}"/*_pub.* "${KEYS_DIR}/tls-cert.pem" "${KEYS_DIR}/ca-cert.pem"
    
    echo -e "${GREEN}✓ Server keys generated successfully!${NC}"
    echo "  Location: ${KEYS_DIR}/"
}

# ============================================================================
# Generate keys for DATABASE
# ============================================================================
generate_database_keys() {
    echo -e "${BLUE}=== Generating keys for DATABASE ===${NC}"
    
    KEYS_DIR="deathnode-database/keys"
    mkdir -p "$KEYS_DIR"
    
    # Only TLS keys needed for database
    echo -e "${GREEN}1. Generating TLS certificate (PostgreSQL)...${NC}"
    
    openssl genrsa -out "${KEYS_DIR}/tls-key.pem" 2048
    
    openssl req -new -key "${KEYS_DIR}/tls-key.pem" \
        -out "${KEYS_DIR}/tls-csr.pem" \
        -subj "/C=PT/ST=Lisbon/L=Lisbon/O=DeathNode/OU=Database/CN=deathnode-database"
    
    if [ ! -f "ca/ca-key.pem" ] || [ ! -f "ca/ca-cert.pem" ]; then
        echo "Error: CA not found. Run '$0 ca' first"
        exit 1
    fi
    
    openssl x509 -req -in "${KEYS_DIR}/tls-csr.pem" \
        -CA "ca/ca-cert.pem" -CAkey "ca/ca-key.pem" -CAcreateserial \
        -out "${KEYS_DIR}/tls-cert.pem" -days 365 \
        -extfile <(printf "subjectAltName=DNS:deathnode-database,IP:127.0.0.1,IP:192.168.0.200")
    
    cp "ca/ca-cert.pem" "${KEYS_DIR}/ca-cert.pem"
    rm "${KEYS_DIR}/tls-csr.pem"
    
    chmod 600 "${KEYS_DIR}/tls-key.pem"
    chmod 644 "${KEYS_DIR}/tls-cert.pem" "${KEYS_DIR}/ca-cert.pem"
    
    echo -e "${GREEN}> Database keys generated successfully!${NC}"
    echo "  Location: ${KEYS_DIR}/"
}

generate_db_client_keys() {
    KEYS_DIR="deathnode-server/server-data/db-client"
    mkdir -p "$KEYS_DIR"

    echo -e "${GREEN} Generating DB Client keys...${NC}"

    # 1. Private key (PEM format - Standard OpenSSL)
    openssl genrsa -out "$KEYS_DIR/client-key.pem" 2048

    # 2. CONVERT TO PKCS#8 DER (REQUIRED for Java JDBC Driver)
    # This is the step missing in your original script
    openssl pkcs8 -topk8 -inform PEM -outform DER \
        -in "$KEYS_DIR/client-key.pem" \
        -out "$KEYS_DIR/client-key.pk8" \
        -nocrypt

    # 3. CSR
    openssl req -new \
        -key "$KEYS_DIR/client-key.pem" \
        -out "$KEYS_DIR/client.csr" \
        -subj "/C=PT/ST=Lisbon/L=Lisbon/O=DeathNode/OU=DatabaseClient/CN=dn_admin"

    # 4. Sign with CA
    openssl x509 -req \
        -in "$KEYS_DIR/client.csr" \
        -CA ca/ca-cert.pem \
        -CAkey ca/ca-key.pem \
        -CAcreateserial \
        -out "$KEYS_DIR/client-cert.pem" \
        -days 365

    # 5. Trust chain
    cp ca/ca-cert.pem "$KEYS_DIR/ca-cert.pem"

    # Set Permissions
    chmod 600 "$KEYS_DIR/client-key.pem" "$KEYS_DIR/client-key.pk8"
    chmod 644 "$KEYS_DIR/client-cert.pem" "$KEYS_DIR/ca-cert.pem"

    rm "$KEYS_DIR/client.csr"
    
    echo -e "${GREEN}> DB Client keys generated (including .pk8 for Java)!${NC}"
}



# ============================================================================
# Main
# ============================================================================

case "$ENTITY_TYPE" in
    ca)
        generate_ca
        ;;
    client)
        generate_client_keys
        ;;
    server)
        generate_server_keys
        ;;
    database)
        generate_database_keys
        generate_db_client_keys
        ;;
    *)
        echo "Error: Unknown entity type '$ENTITY_TYPE'"
        exit 1
        ;;
esac

echo ""
echo -e "${GREEN}=== Key generation complete! ===${NC}"