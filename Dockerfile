# syntax=docker/dockerfile:1
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /src
COPY . .
RUN --mount=type=cache,target=/root/.m2 mvn -B -Dmaven.test.skip=true package

FROM eclipse-temurin:17-jre-jammy AS runtime
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl fontconfig \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --gid 10001 zjerp \
    && useradd --uid 10001 --gid zjerp --no-create-home zjerp
WORKDIR /app
COPY --from=build /src/zjerp-admin/target/zjerp-admin.jar app.jar
COPY --from=build /src/wbcard/ ./wbcard/
RUN mkdir -p /app/upload /app/logs && chown -R zjerp:zjerp /app
USER zjerp
ENV TZ=Asia/Shanghai \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -Djava.awt.headless=true" \
    RUOYI_PROFILE=/app/upload
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=10s --start-period=90s --retries=5 \
    CMD curl -fsS http://127.0.0.1:8080/health | grep -q '"code":200' || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
