docker stop deathnode-db
docker rm deathnode-db
docker volume rm deathnode-db-certs

chmod 600 keys/tls-key.pem
chmod 644 keys/tls-cert.pem
chmod 644 keys/ca-cert.pem

# Create a temporary container to copy files into the volume
docker volume create deathnode-db-certs
docker run --rm -v deathnode-db-certs:/certs -v $(pwd)/keys:/src alpine sh -c \
   "cp /src/tls-cert.pem /certs/server.crt && \
   cp /src/tls-key.pem /certs/server.key && \
   cp /src/ca-cert.pem /certs/root.crt && \
   chown 999:999 /certs/* && \
   chmod 600 /certs/server.key && \
   chmod 644 /certs/server.crt /certs/root.crt"

# Run PostgreSQL with volume mount
docker run -d --name deathnode-db \
  -e POSTGRES_DB=deathnode \
  -e POSTGRES_USER=dn_admin \
  -e POSTGRES_PASSWORD=dn_pass \
  -v deathnode-db-certs:/var/lib/postgresql \
  -v $(pwd)/pg_hba.conf:/var/lib/postgresql/data/pg_hba.conf \
  -p 5432:5432 \
  postgres:18 \
  -c ssl=on \
  -c ssl_cert_file=/var/lib/postgresql/server.crt \
  -c ssl_key_file=/var/lib/postgresql/server.key \
  -c ssl_ca_file=/var/lib/postgresql/root.crt
