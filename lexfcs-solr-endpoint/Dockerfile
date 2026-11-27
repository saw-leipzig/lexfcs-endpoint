# ---------------------------------------------------------------------------
FROM maven:3.10.2-eclipse-temurin-21-resolute AS builder

WORKDIR /work

COPY pom.xml /work/

# note: that those commands may take a while to finish
# they are used to cache pom.xml plugins and dependencies for faster builds
RUN mvn -B dependency:resolve-plugins
RUN mvn -B dependency:resolve

COPY src /work/src

RUN mvn -B clean package

# ---------------------------------------------------------------------------
FROM jetty:11-jdk21-eclipse-temurin AS jetty

RUN java -jar $JETTY_HOME/start.jar --add-modules=plus

COPY --from=builder /work/target/fcs-endpoint.war /var/lib/jetty/webapps/ROOT.war

# ---------------------------------------------------------------------------
