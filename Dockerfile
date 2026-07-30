FROM tomcat:9.0.118-jdk17-temurin-jammy

ARG WAR_FILE=build/libs/moca-be-1.0-SNAPSHOT.war

# ROOT.war로 배포해 외부 경로를 /api/v1/...로 고정한다.
RUN rm -rf /usr/local/tomcat/webapps/*
COPY ${WAR_FILE} /usr/local/tomcat/webapps/ROOT.war

EXPOSE 8080
