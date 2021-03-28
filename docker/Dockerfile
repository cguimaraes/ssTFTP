FROM maven:3.6.1-jdk-7-alpine

ENV DEBIAN_FRONTEND=noninteractive

RUN apk upgrade --no-cache && \
    apk add git --no-cache

RUN git clone https://github.com/cguimaraes/ssTFTP.git
WORKDIR /ssTFTP/

RUN mvn clean install

