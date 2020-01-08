FROM gradle:6.0.1-jdk13 AS build
COPY . /
WORKDIR /
RUN gradle test