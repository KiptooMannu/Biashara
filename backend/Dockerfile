# ─────────────────────────────────────────────────────────────────────────────
# BIASHARA API — production container image.
#
# Multi-stage: a full Maven/JDK image builds the jar, and only a JRE plus the
# extracted application ships. Build context is this directory (backend/).
#
#   docker build -t biashara-api .
#   docker run --rm -p 8080:8080 biashara-api
# ─────────────────────────────────────────────────────────────────────────────

# ── stage 1: build ───────────────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Resolved from the POM alone, so this layer is reused on every build where the
# dependency list has not changed — which is most of them.
COPY pom.xml .
RUN mvn -B -ntp dependency:go-offline

COPY src ./src
RUN mvn -B -ntp -DskipTests clean package

# Split the fat jar into layers that change at different rates. Dependencies
# rarely move; application classes move every commit. Copying them as separate
# COPY instructions below means a code change re-uploads a few hundred kilobytes
# instead of the whole ~60 MB jar.
RUN cp target/biashara-erp-*.jar app.jar \
 && java -Djarmode=tools -jar app.jar extract --layers --launcher --destination extracted

# ── stage 2: runtime ─────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-jammy AS runtime

# Unprivileged user. A compromised process should not own the filesystem it
# runs on, and Render will not grant anything this image asks for beyond it.
RUN groupadd --system --gid 1001 biashara \
 && useradd  --system --uid 1001 --gid biashara --home /app biashara

WORKDIR /app

# Ordered least- to most-frequently changed.
COPY --from=build --chown=1001:1001 /build/extracted/dependencies/          ./
COPY --from=build --chown=1001:1001 /build/extracted/spring-boot-loader/    ./
COPY --from=build --chown=1001:1001 /build/extracted/snapshot-dependencies/ ./
COPY --from=build --chown=1001:1001 /build/extracted/application/           ./

USER 1001

# MaxRAMPercentage rather than a fixed -Xmx: the JVM reads the cgroup limit, so
# the same image is correct on a 512 MB Render instance and a 4 GB one.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:InitialRAMPercentage=40 -XX:+ExitOnOutOfMemoryError"
ENV SPRING_PROFILES_ACTIVE=neon

# Documentation only — Render overrides this with $PORT, which the app reads.
EXPOSE 8080

# exec form via sh so JAVA_OPTS is word-split; exec so the JVM is PID 1 and
# receives SIGTERM directly on deploy and shutdown.
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]
