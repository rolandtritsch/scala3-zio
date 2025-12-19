# =============================================================================
# Stage 1: Builder - Compile Scala code and create fat JAR
# =============================================================================
FROM eclipse-temurin:21-jdk AS builder

# Install SBT
RUN apt-get update && \
    apt-get install -y curl && \
    curl -L "https://github.com/sbt/sbt/releases/download/v1.11.7/sbt-1.11.7.tgz" | tar zxf - -C /usr/local && \
    ln -s /usr/local/sbt/bin/sbt /usr/bin/sbt && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

WORKDIR /build

# Copy SBT build files first for better layer caching
# This layer only rebuilds when build configuration changes
COPY project/build.properties project/
COPY project/plugins.sbt project/
COPY build.sbt .

# Download dependencies (cached layer)
# This creates a layer that only rebuilds when dependencies change
RUN sbt update

# Copy source code
COPY src/ src/

# Build fat JAR with assembly
# Skip tests in Docker build (run tests in CI separately)
RUN sbt "set test in assembly := {}" assembly

# =============================================================================
# Stage 2: Runtime - Minimal JRE image
# =============================================================================
FROM eclipse-temurin:21-jre

# Create non-root user for security
RUN groupadd -r appuser && useradd -r -g appuser appuser

WORKDIR /app

# Copy the fat JAR from builder stage
COPY --from=builder /build/target/scala-3.7.4/scala3-zio-template.jar app.jar

# Change ownership to non-root user
RUN chown -R appuser:appuser /app

# Switch to non-root user
USER appuser

# Expose application port
EXPOSE 8080

# Set JVM options for containerized environment
ENV JAVA_OPTS="-XX:+UseContainerSupport \
               -XX:MaxRAMPercentage=75.0 \
               -XX:InitialRAMPercentage=50.0 \
               -XX:+UseG1GC \
               -XX:+UseStringDeduplication \
               -Djava.security.egd=file:/dev/./urandom"

# Health check using the /health endpoint
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/health || exit 1

# Run the application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
