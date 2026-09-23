# Runtime image consumes the JAR built by the project launcher on the host.
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

# Install tzdata for proper Shanghai timezone inside container
RUN sed -i '/jammy-updates/d; /jammy-backports/d; s|http://|https://|g' /etc/apt/sources.list && \
    apt-get update -o Acquire::Retries=5 && \
    apt-get install -y --no-install-recommends libreoffice-writer libreoffice-impress tzdata fonts-noto-cjk && \
    rm -rf /var/lib/apt/lists/* && \
    cp /usr/share/zoneinfo/Asia/Shanghai /etc/localtime && \
    echo "Asia/Shanghai" > /etc/timezone

COPY target/classroom-backend-0.0.1-SNAPSHOT.jar app.jar

# Create directory for uploads
RUN mkdir -p /app/runtime/uploads/faces

EXPOSE 8080

ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]
