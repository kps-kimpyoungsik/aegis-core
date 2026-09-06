FROM eclipse-temurin:21-jre
WORKDIR /opt/aegis
COPY dist/aegis-server.jar /opt/aegis/aegis-server.jar
USER 65532:65532
EXPOSE 8080
ENTRYPOINT ["java","-jar","/opt/aegis/aegis-server.jar"]
