# ---- ビルド ----
FROM eclipse-temurin:25-jdk AS build
WORKDIR /app
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -q -B dependency:go-offline
COPY src src
RUN ./mvnw -q -B -DskipTests package \
    && java -Djarmode=tools -jar target/*.jar extract --destination extracted \
    && mv extracted/*.jar extracted/app.jar

# ---- 実行 ----
FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /app/extracted/ ./

ENV SPRING_PROFILES_ACTIVE=prod
# 無料枠（メモリ 512MB・CPU 0.1）向け：
# - ヒープをコンテナのメモリの 70% までにする
# - JIT の最適化を軽いものだけにして、弱い CPU を応答に回す
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -XX:+UseSerialGC -Xss512k -XX:TieredStopAtLevel=1 -Duser.timezone=Asia/Tokyo"
# 「今日」を日本時間で判定する（Render のサーバーは UTC で動いているため）
ENV TZ=Asia/Tokyo

# 起動を速くする AOT キャッシュを作る（Java 25）。
# 一度アプリを起動してすぐ終了させ、そのとき読み込んだクラスを app.aot に保存する。
# この時だけはメモリ上の H2 を使うので、本物のデータベースには接続しない。
RUN DATABASE_URL=jdbc:h2:mem:training REMEMBER_ME_KEY=training \
    java -XX:AOTCacheOutput=app.aot -Dspring.context.exit=onRefresh -jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-XX:AOTCache=app.aot", "-jar", "app.jar"]
