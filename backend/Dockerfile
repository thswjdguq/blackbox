FROM gradle:9.8.2-jdk25-alpine AS builder
WORKDIR /app
COPY build.gradle settings.gradle ./
RUN gradle dependencies --no-daemon || true
COPY src/ src/
RUN gradle bootJar --no-daemon -x test \
    || gradle bootJar --no-daemon -x test --offline

FROM eclipse-temurin:25-jre-alpine
WORKDIR /app
RUN mkdir -p /data/uploads
COPY --from=builder /app/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
