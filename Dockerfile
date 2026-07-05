FROM maven:3.9-eclipse-temurin-17 AS build

WORKDIR /src
COPY pom.xml .
COPY src ./src
RUN mvn -B -DskipTests package

FROM maven:3.9-eclipse-temurin-17

WORKDIR /workspace
ENV TERM=xterm-256color

COPY --from=build /src/target/yucli-19.0.0.jar /opt/yucli/yucli.jar

ENTRYPOINT ["java", "-jar", "/opt/yucli/yucli.jar"]
