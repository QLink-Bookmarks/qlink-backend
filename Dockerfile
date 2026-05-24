FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

ARG APP_ENV=dev

COPY build/libs/qlink-backend-all.jar /app/qlink-backend-all.jar
COPY src/main/resources/application.yaml /app/config/application.yaml
COPY src/main/resources/application-dev.yaml /app/config/application-dev.yaml

ENV APP_ENV=${APP_ENV}

EXPOSE 8080

CMD ["sh", "-c", "java -jar /app/qlink-backend-all.jar -config=/app/config/application.yaml -config=/app/config/application-${APP_ENV}.yaml"]
