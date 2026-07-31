FROM tomcat:9.0.118-jdk17-temurin-jammy

ARG WAR_FILE=build/libs/moca-be-1.0-SNAPSHOT.war

# ROOT.war로 배포해 외부 경로를 /api/v1/...로 고정한다.
RUN apt-get update \
    && apt-get install --no-install-recommends --yes curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system tomcat \
    && useradd --system --gid tomcat --home /usr/local/tomcat --shell /usr/sbin/nologin tomcat \
    && rm -rf /usr/local/tomcat/webapps/* \
    && chown -R tomcat:tomcat /usr/local/tomcat/logs /usr/local/tomcat/temp /usr/local/tomcat/webapps /usr/local/tomcat/work
COPY --chown=tomcat:tomcat ${WAR_FILE} /usr/local/tomcat/webapps/ROOT.war

USER tomcat

EXPOSE 8080
