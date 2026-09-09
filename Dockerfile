FROM eclipse-temurin:21-jdk AS build

WORKDIR /workspace

# Root settings configure the Android projects too, so their build descriptors
# remain available even though their source is outside the server build context.
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle ./gradle
COPY app/androidApp/build.gradle.kts ./app/androidApp/build.gradle.kts
COPY app/shared/build.gradle.kts ./app/shared/build.gradle.kts
COPY core/build.gradle.kts ./core/build.gradle.kts
COPY core/src ./core/src
COPY server/build.gradle.kts ./server/build.gradle.kts
COPY server/src/main ./server/src/main

RUN chmod +x ./gradlew \
    && ./gradlew --no-daemon :server:installDist

FROM eclipse-temurin:21-jre

RUN groupadd --system app \
    && useradd --system --gid app --create-home --home-dir /app app

WORKDIR /app
COPY --from=build --chown=app:app /workspace/server/build/install/server ./

USER app

ENV PORT=8080
ENV JAVA_OPTS="-Xms128m -Xmx384m -XX:+ExitOnOutOfMemoryError"

EXPOSE 8080

ENTRYPOINT ["/app/bin/server"]
