FROM maven:3.9.9-eclipse-temurin-17 AS builder

WORKDIR /build
COPY . .
RUN mvn -pl easy-agent-app -am -DskipTests package

FROM eclipse-temurin:17-jre

WORKDIR /app
ENV TZ=Asia/Shanghai
ENV JAVA_OPTS="-Xms512m -Xmx1024m"

RUN ln -snf /usr/share/zoneinfo/${TZ} /etc/localtime \
    && echo "${TZ}" > /etc/timezone \
    && mkdir -p /app/data/log

COPY --from=builder /build/easy-agent-app/target/easy-agent-app.jar /app/easy-agent-app.jar

EXPOSE 8091

ENTRYPOINT ["sh", "-c", "java ${JAVA_OPTS} -jar /app/easy-agent-app.jar"]
