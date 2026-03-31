# Multi-stage build for Car-checker Spring Boot application
# Stage 1: Build
FROM maven:3.9.8-eclipse-temurin-25-alpine AS builder

WORKDIR /build

# Copy all source files
COPY . .

# Build the project; only carwatch-boot will be used at runtime
RUN mvn clean package -DskipTests -q


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
