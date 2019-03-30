# build stage
FROM maven:3.6-jdk-8-alpine
COPY . /mps
RUN cd /mps && mvn package