FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /app
COPY gradlew gradlew
COPY gradle gradle
COPY build.gradle settings.gradle ./
COPY src src
RUN ./gradlew installDist --no-daemon

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/build/install/minisearch /app
ENV PORT=8080
ENV INDEX_DIR=/data/segments
EXPOSE 8080
VOLUME ["/data"]
ENTRYPOINT ["/app/bin/minisearch"]
