FROM maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /workspace
COPY pom.xml .
COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S kamoved && adduser -S kamoved -G kamoved
COPY --from=build /workspace/target/kamoved-backend-*.jar app.jar
USER kamoved
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

