# -------- Build stage --------
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace

# Cache deps
COPY pom.xml .
RUN mvn -q -B -e -DskipTests dependency:go-offline

# Build
COPY src ./src
RUN mvn -q -B -e -DskipTests package && \
    mkdir -p target/dep && \
    (cd target/dep && jar -xf ../*.jar)

# -------- Runtime stage --------
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Non-root user
RUN addgroup -S app && adduser -S app -G app && chown -R app:app /app
USER app

# Copy exploded layers so unchanged layers cache well
COPY --from=build /workspace/target/dep/BOOT-INF/lib /app/lib
COPY --from=build /workspace/target/dep/META-INF /app/META-INF
COPY --from=build /workspace/target/dep/BOOT-INF/classes /app/classes

ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"
EXPOSE 8080

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -cp /app:/app/classes:/app/lib/* com.legacyforge.LegacyForgeApplication"]
