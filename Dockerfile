FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --chown=10001:10001 target/hello-world.jar /app/app.jar
USER 10001:10001
ENV PORT=8080
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
