FROM maven:3.9.14-eclipse-temurin-11-alpine

ENV DEBIAN_FRONTEND=noninteractive

RUN apk upgrade --no-cache && \
    apk add git --no-cache

RUN mkdir -p /ssTFTP/
COPY . /ssTFTP/

WORKDIR /ssTFTP/
RUN mvn clean install

