
FROM eclipse-temurin:25-jdk

WORKDIR /app

COPY RubiumLead .

RUN chmod +x mvnw
RUN ./mvnw clean package -DskipTests

CMD ["java", "-jar", "target/com-0.0.1-SNAPSHOT.jar"]