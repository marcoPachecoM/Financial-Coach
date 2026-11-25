# Production image of the API (Cloud Run). Build from backend/: docker build -t financial-coach-api .
# See docs/deploy.md.

# --- Build -----------------------------------------------------------------------------------
FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace

# Dependencies first, in their own layer: only re-downloaded when pom.xml changes.
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN ./mvnw -B -q dependency:go-offline

COPY src src
# Tests run in CI before an image is ever built (deploy.yml only runs after CI is green).
RUN ./mvnw -B -q -DskipTests package \
    && cp target/backend-*.jar app.jar

# --- Run -------------------------------------------------------------------------------------
FROM eclipse-temurin:25-jre

# tzdata, so the time zone below resolves (normally already in the Temurin image).
RUN apt-get update \
    && apt-get install -y --no-install-recommends tzdata \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --uid 10001 --no-create-home app

WORKDIR /app
COPY --from=build /workspace/app.jar app.jar
USER app

# "Today" (goals, recurring transactions, the current month) is computed in this zone: the
# container would otherwise run in UTC. Set twice: TZ for the OS, -Duser.timezone for the JVM.
# The heap follows the memory Cloud Run gives the container. Both can be overridden at deploy time.
ENV TZ=America/Mexico_City \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -Duser.timezone=America/Mexico_City" \
    SPRING_PROFILES_ACTIVE=prod \
    LOG_FILE=""

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
