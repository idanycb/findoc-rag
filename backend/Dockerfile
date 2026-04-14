# --- Stage 1: Build ---
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /app
COPY . .
RUN ./mvnw -DskipTests clean package spring-boot:repackage && \
    rm -f target/*plain.jar target/*original.jar target/*exec.jar 2>/dev/null || true && \
    find target -name "*.jar" | head -1 | xargs -I {} mv {} target/app.jar

# --- Stage 2: Runtime ---
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

COPY --from=build /app/target/app.jar app.jar
RUN chown -R 1000:1000 /app

USER 1000:1000

ENTRYPOINT ["java", "-jar", "app.jar"]
