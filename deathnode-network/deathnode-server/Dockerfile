# build stage
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /app

# Copy the prepared local dependency artifact directory
COPY local-repo /root/.m2/repository/

# Add a verification step to ensure the artifact is in the cache 
RUN ls -l /root/.m2/repository/com/deathnode/deathnode-tool/1.0.0/

COPY pom.xml .
COPY src ./src
# build the jar (This command will now find the deathnode-tool in the container's Maven cache)
RUN mvn clean package -DskipTests

# runtime stage
FROM eclipse-temurin:21-jdk-alpine
WORKDIR /app
COPY --from=build /app/target/deathnode-server-0.0.1-SNAPSHOT.jar /app/deathnode-server.jar
CMD ["java", "-jar", "deathnode-server.jar"]