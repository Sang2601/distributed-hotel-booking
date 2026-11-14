# syntax=docker/dockerfile:1
# ---- Build: biên dịch bằng Maven trong container, máy chủ không cần cài Maven ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
# Cache thư viện Maven dùng chung giữa các lần build; tự thử lại khi mạng chập chờn
RUN --mount=type=cache,target=/root/.m2,sharing=locked \
    mvn -q -B -Daether.connector.http.retryHandler.count=5 dependency:go-offline
COPY src ./src
RUN --mount=type=cache,target=/root/.m2,sharing=locked \
    mvn -q -B -Daether.connector.http.retryHandler.count=5 package -DskipTests

# ---- Run: chỉ chứa JRE + file jar ----
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=70", "-XX:+UseSerialGC", "-Xss512k", "-XX:TieredStopAtLevel=1", "-jar", "app.jar"]
