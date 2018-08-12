FROM maven:alpine as BUILD

WORKDIR /usr/src/maven

# cache dependencies from pom
COPY ./pom.xml ./pom.xml
RUN mvn -B -s /usr/share/maven/ref/settings-docker.xml dependency:resolve-plugins dependency:resolve clean package

# build fat jar
COPY ./src ./src
RUN mvn -B -s /usr/share/maven/ref/settings-docker.xml clean package

####################################################################

FROM openjdk:alpine

ENV JAR_FILENAME=car-accidents-map-be-fat.jar

WORKDIR /app

# fat jar
COPY --from=BUILD /usr/src/maven/target/$JAR_FILENAME ./

# default config file
COPY ./example_config.json ./config/config.json
VOLUME ["/app/config"]

EXPOSE 8080
CMD java -jar ${JAR_FILENAME} -conf config/config.json
