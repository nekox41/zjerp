FROM eclipse-temurin:17.0.16_8-jre-ubi9-minimal

WORKDIR /app

COPY ./zjerp-admin/target/zjerp-admin.jar app.jar

VOLUME /app/upload

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
