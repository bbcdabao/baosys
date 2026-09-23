ARG BASE_IMAGE=eclipse-temurin:17-jre
FROM ${BASE_IMAGE}

WORKDIR /baosys-manager-web
COPY target/baosys-manager-web.jar app.jar

ENV TZ=Asia/Shanghai
ENV JAVA_OPTS="-Xms512m -Xmx512m -Djava.security.egd=file:/dev/./urandom"
ENV ARGS=""
EXPOSE 8080

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar $ARGS"]
