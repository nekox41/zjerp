FROM eclipse-temurin:17.0.16_8-jre-ubi9-minimal

WORKDIR /app

COPY ./zjerp-admin/target/zjerp-admin.jar app.jar

VOLUME /app/upload
VOLUME /app/wbcard

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
