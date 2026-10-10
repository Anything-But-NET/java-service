FROM eclipse-temurin:25-jdk-noble AS build

WORKDIR /app

COPY gradlew gradlew
COPY gradle gradle
COPY build.gradle* settings.gradle* ./

RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon dependencies || true

COPY src src

RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon clean bootJar

FROM eclipse-temurin:25-jre-noble

WORKDIR /app

COPY --from=build /app/build/libs/*.jar app.jar

ENV JAVA_OPTS=""

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]