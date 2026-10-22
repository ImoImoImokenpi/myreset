# ---- ビルド ----
FROM eclipse-temurin:25-jdk AS build
WORKDIR /app
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -q -B dependency:go-offline
COPY src src
RUN ./mvnw -q -B -DskipTests package && cp target/*.jar app.jar

# ---- 実行 ----
FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /app/app.jar app.jar
ENV SPRING_PROFILES_ACTIVE=prod
# 無料枠（メモリ 512MB）で落ちないよう、ヒープをコンテナのメモリの 70% までにする
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -XX:+UseSerialGC -Xss512k"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
