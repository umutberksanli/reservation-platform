# syntax=docker/dockerfile:1

# ---- 1) Derleme: JDK + Maven Wrapper ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# Once yalnizca bagimliliklar: kaynak kod degisince bu katman onbellekten gelir.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -ntp dependency:go-offline

COPY src/ src/
# Testler burada calistirilmaz: CI'daki "test" isi gercek MySQL ile tum testleri kosar,
# imaj ancak o is basarili olduktan sonra kurulur (bkz. .github/workflows/ci.yml).
RUN ./mvnw -B -ntp package -DskipTests \
    && cp target/event-reservation-*.jar /workspace/app.jar

# ---- 2) Calisma: yalnizca JRE, root olmayan kullanici ----
FROM eclipse-temurin:21-jre
RUN groupadd --system app && useradd --system --gid app --uid 10001 --no-create-home app
WORKDIR /app
COPY --from=build /workspace/app.jar /app/app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
