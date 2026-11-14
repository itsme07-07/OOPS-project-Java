FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml .
COPY src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /build/target/relay.jar /app/relay.jar
ENV JAVA_TOOL_OPTIONS="-Xmx256m"
EXPOSE 8080
CMD ["java", "-jar", "/app/relay.jar", "--no-demo"]
