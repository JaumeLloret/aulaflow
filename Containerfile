FROM docker.io/library/eclipse-temurin:26-jre-noble

RUN groupadd --gid 10001 aulaflow \
    && useradd \
        --uid 10001 \
        --gid 10001 \
        --no-create-home \
        --home-dir /nonexistent \
        --shell /usr/sbin/nologin \
        --no-log-init \
        aulaflow \
    && mkdir -p /var/lib/aulaflow \
    && chown 10001:10001 /var/lib/aulaflow

WORKDIR /opt/aulaflow

COPY target/aulaflow-1.0.0.jar /opt/aulaflow/aulaflow.jar
COPY container/healthcheck.sh /opt/aulaflow/healthcheck.sh

RUN chmod 0555 /opt/aulaflow/healthcheck.sh

ENV AULAFLOW_HTTP_HOST=0.0.0.0 \
    AULAFLOW_HTTP_PORT=8080 \
    AULAFLOW_DB_PATH=/var/lib/aulaflow/aulaflow.db

EXPOSE 8080

USER 10001:10001

HEALTHCHECK \
    --interval=30s \
    --timeout=5s \
    --start-period=10s \
    --retries=3 \
    CMD ["/opt/aulaflow/healthcheck.sh"]

ENTRYPOINT ["java", "-jar", "/opt/aulaflow/aulaflow.jar"]
