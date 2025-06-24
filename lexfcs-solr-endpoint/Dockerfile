# ---------------------------------------------------------------------------
FROM maven:3.9.9-eclipse-temurin-11-focal AS builder

WORKDIR /work

COPY pom.xml /work/

# note: that those commands may take a while to finish
# they are used to cache pom.xml plugins and dependencies for faster builds
RUN mvn -ntp dependency:resolve-plugins
RUN mvn -ntp dependency:resolve

COPY src /work/src

RUN mvn -ntp clean package

# ---------------------------------------------------------------------------
FROM jetty:10-jdk11-eclipse-temurin AS jetty

RUN java -jar $JETTY_HOME/start.jar --add-modules=plus

COPY --from=builder /work/target/fcs-endpoint.war /var/lib/jetty/webapps/ROOT.war

USER root
RUN apt update ; \
    apt install -y --no-install-recommends \
        git \
        maven \
        ; \
    rm -rf /var/lib/apt/lists/*

COPY pom.xml /work/
COPY src /work/src
COPY .env /work/
RUN chown -R jetty:jetty /work/

USER jetty

RUN ln -s /work/

# ---------------------------------------------------------------------------
