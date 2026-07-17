FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q dependency:go-offline
COPY src ./src
RUN mvn -q package -DskipTests

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080

# Render's free-tier instance has only 512MB RAM total. With no JVM memory
# flags, the JVM's container-aware default (25% of the container's memory
# limit for max heap) still leaves enough headroom for metaspace + thread
# stacks + Netty/WebClient buffers + JIT code cache to occasionally exceed
# 512MB under load, which is what "exited with status 137" (SIGKILL, the
# OOM killer) means - it also explains the earlier multi-minute boot time
# (GC/memory pressure during startup, not just CPU throttling). These flags
# cap heap explicitly and use SerialGC (far less memory overhead than the
# default G1GC, and fine for a single-core free-tier instance). Overridable
# via Render's JAVA_OPTS env var without rebuilding the image.
ENV JAVA_OPTS="-Xmx256m -XX:MaxMetaspaceSize=160m -XX:+UseSerialGC -Xss512k"
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
