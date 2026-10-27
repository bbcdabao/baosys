ARG BASE_IMAGE=eclipse-temurin:17-jre
FROM ${BASE_IMAGE}

WORKDIR /baosys-manager
COPY target/baosys-manager.jar app.jar

ENV TZ=Asia/Shanghai
ENV JAVA_OPTS="-Xms512m -Xmx512m -Djava.security.egd=file:/dev/./urandom"
ENV ARGS=""
EXPOSE 9090

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar $ARGS"]
