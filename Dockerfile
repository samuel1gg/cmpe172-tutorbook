# ---------------------------------------------------------------------------
# TutorBook - multi-stage build
#   stage 1 (build):   JDK + Maven wrapper compile the app into one jar
#   stage 2 (runtime): only a JRE and that jar, running as a non-root user
# Tests are not run here; the GitHub Actions workflow runs them on every push.
# ---------------------------------------------------------------------------
FROM eclipse-temurin:17-jdk AS build
WORKDIR /app

# Download dependencies first, in their own layer, so code changes
# do not re-download Maven dependencies on every build.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline

COPY src/ src/
RUN ./mvnw -B -q package -DskipTests

# ---------------------------------------------------------------------------
FROM eclipse-temurin:17-jre
WORKDIR /app

RUN useradd --system --uid 1001 tutorbook
COPY --from=build /app/target/tutorbook-*.jar app.jar
USER tutorbook

EXPOSE 8080

# Docker marks the container unhealthy if GET /health stops answering 200.
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD curl -fsS http://localhost:8080/health > /dev/null || exit 1

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
