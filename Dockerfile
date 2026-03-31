FROM eclipse-temurin:21-jdk AS builder
WORKDIR /build

ARG SERVICE_NAME

COPY gradlew .
COPY gradle gradle
COPY build.gradle .
COPY settings.gradle .

COPY common/build.gradle common/
COPY common-observability/build.gradle common-observability/
COPY ${SERVICE_NAME}/build.gradle ${SERVICE_NAME}/

RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew :${SERVICE_NAME}:dependencies --no-daemon || true

COPY . .
RUN --mount=type=cache,target=/root/.gradle \
    chmod +x ./gradlew && \
    ./gradlew :${SERVICE_NAME}:bootJar -x test --no-daemon --stacktrace

FROM eclipse-temurin:21-jre
WORKDIR /app

RUN useradd -ms /bin/bash appuser
USER appuser

ARG SERVICE_NAME
COPY --from=builder --chown=appuser:appuser /build/${SERVICE_NAME}/build/libs/*.jar app.jar

STOPSIGNAL SIGTERM

ENTRYPOINT ["java", \
            "-XX:+UseContainerSupport", \
            "-XX:MaxRAMPercentage=75.0", \
            "-XX:InitialRAMPercentage=50.0", \
            "-XX:+ExitOnOutOfMemoryError", \
            "-XX:+HeapDumpOnOutOfMemoryError", \
            "-XX:HeapDumpPath=/tmp/oom_dump.hprof", \
            "-Djava.security.egd=file:/dev/./urandom", \
            "-Duser.timezone=Asia/Seoul", \
            "-jar", "app.jar"]