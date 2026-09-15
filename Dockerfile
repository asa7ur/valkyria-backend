# --- Etapa 1: compilar el jar ---
FROM maven:3-eclipse-temurin-25 AS builder
WORKDIR /build
COPY pom.xml .
COPY src ./src
# La caché de ~/.m2 evita volver a descargar las dependencias en cada build
RUN --mount=type=cache,target=/root/.m2 mvn -B -DskipTests package

# --- Etapa 2: imagen final, solo con el JRE y el jar ---
FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=builder /build/target/*.jar app.jar

# Sin privilegios: 'ubuntu' (uid 1000) ya existe en la imagen base y es el dueño de uploads/ y certs/ en el servidor
USER ubuntu

# Los secretos NO van en la imagen: se pasan como variables de entorno al arrancar (env_file en compose)
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
