FROM eclipse-temurin:21-jdk AS builder

WORKDIR /app

COPY gradlew .
COPY gradle gradle
COPY build.gradle .
COPY settings.gradle .
COPY gradle.properties .

RUN chmod +x ./gradlew
RUN ./gradlew --no-daemon dependencies > /dev/null 2>&1 || true

COPY src src

RUN ./gradlew --no-daemon clean bootJar

FROM eclipse-temurin:21-jre

RUN addgroup --system --gid 1000 worker \
    && adduser --system --uid 1000 --ingroup worker --disabled-password worker

WORKDIR /app

COPY --from=builder /app/build/libs/*.jar /app/app.jar

USER worker:worker

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
