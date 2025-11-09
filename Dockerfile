FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace/app

# Install project dependencies first to leverage Docker layer caching
COPY .mvn/ .mvn/
COPY mvnw mvnw
COPY pom.xml pom.xml
RUN chmod +x mvnw
RUN ./mvnw -B dependency:go-offline

# Copy the rest of the source and build the application
COPY src src
RUN ./mvnw -B clean package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app

# Copy the fat jar produced by the Spring Boot build
COPY --from=build /workspace/app/target/*.jar app.jar
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
