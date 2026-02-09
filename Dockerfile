FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /build

ARG SERVICE_NAME

COPY gradlew .
COPY gradle gradle
COPY build.gradle .
COPY settings.gradle .
COPY /**/build.gradle ./

RUN ./gradlew dependencies --no-daemon || true

COPY . .

RUN chmod +x ./gradlew
RUN ./gradlew :${SERVICE_NAME}:bootJar --no-daemon

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

ARG SERVICE_NAME

COPY --from=builder /build/${SERVICE_NAME}/build/libs/*.jar app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]