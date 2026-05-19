# Multi-stage build for Car-checker Spring Boot application
# Stage 1: Build
FROM maven:3.9.14-eclipse-temurin-25-alpine AS builder

WORKDIR /build

# Download dependencies first — this layer is cached as long as pom.xml files don't change.
COPY pom.xml .
COPY carwatch-domain/pom.xml carwatch-domain/
COPY carwatch-application/pom.xml carwatch-application/
COPY carwatch-infrastructure/pom.xml carwatch-infrastructure/
COPY carwatch-web/pom.xml carwatch-web/
COPY carwatch-boot/pom.xml carwatch-boot/
COPY carwatch-reports/pom.xml carwatch-reports/
RUN mvn dependency:go-offline -q

# Copy source and build; integration tests run against in-memory SQLite.
COPY . .
RUN mvn clean package -q


# Stage 2: Runtime
FROM eclipse-temurin:25-jre-alpine

# Set application home
ENV CARWATCH_HOME=/opt/carwatch \
    SPRING_PROFILES_ACTIVE=prod \
    JAVA_OPTS="-Xms128m -Xmx384m"

WORKDIR ${CARWATCH_HOME}

# Create data and logs directories
RUN mkdir -p data logs

# Copy the built jar from builder stage
COPY --from=builder /build/carwatch-boot/target/carwatch-boot-*.jar carwatch-boot.jar

EXPOSE 8080

CMD ["sh", "-c", "java $JAVA_OPTS -Dspring.profiles.active=$SPRING_PROFILES_ACTIVE -jar carwatch-boot.jar"]
