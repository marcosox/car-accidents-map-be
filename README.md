Car accidents map - backend
=========================

This project is a rewrite of an old assignment for the [InfoVis course](http://www.dia.uniroma3.it/~infovis)
held by Maurizio Patrignani at Roma Tre University.
It's original version is visible [here](https://github.com/marcosox/visualizzazione_incidenti),
with a list of the changes.

This is the backend component.
To visualiza the data, use the [frontend web application](https://github.com/marcosox/car-accidents-map-fe).

### About this tool:

This is an API web server written with [Vert.x](https://vertx.io/) that
reads from a MongoDB instance storing informations about car accidents happened in Rome.
The original data was obtained from the [Rome municipality open data website](http://dati.comune.roma.it/cms/it/incidenti_stradali.page) in CSV format,
then converted to RDF with [Google Refine](http://openrefine.org/) and manipulated in different ways to learn about semantic web technologies
(which included generating an ontology to describe road accidents and using [SPARQL](https://en.wikipedia.org/wiki/SPARQL) to query the data).
Finally it was cleaned and persisted on a MongoDB collection to be used by the [web app](https://github.com/marcosox/car-accidents-map-fe).

### Installation:

1. Import the [example dataset](https://github.com/marcosox/Visualizzazione_incidenti/tree/master/Visualizzazione_incidenti/example_data) into MongoDB:  
`mongoimport --db infovis --collection accidents --file /path/to/incidenti_geolocalizzati.json`  
`mongoimport --db infovis --collection districts --file /path/to/municipi.json`  
Note that `infovis` and `accidents` must match respectively
the database and the collection used inside the class MongoDAO.java  
2. Ensure MongoDB is running
3. Build the project with Maven: `mvn package`. This will create a fat jar in the `target` folder.
	- To run the fat JAR directly after build, use the `exec:exec@run-app` maven target.
4. Run the app with `java -jar path/to/car-accidents-map-fat.jar`
5. Go to [http://localhost:8080/](http://localhost:8080/) to see the list of available endpoints
6. Use the [web app](https://github.com/marcosox/car-accidents-map-fe) to visualize the data

#### Docker

The app can be built and run in a container, check the `Dockerfile`.
The building is separated in two stages, in order to keep the final image as small as possible.
Maven dependencies are cached to the maximum extent possible.

Using docker, the app can be run locally without maven or java at all:

	git clone https://github.com/marcosox/car-accidents-map-be.git
	cd car-accidents-map-be
	docker build . -t car-accidents-map-be
	
	# Run with the default configuration:

	docker run -it -p 8080:8080 car-accidents-map-be
	
	# Or overwrite the default configuration:
	
	cp example_config.json config.json
	# edit the app settings
	vi config.json
	docker build . -t car-accidents-map-be
	docker run -it -v "$(pwd)":/app/config -p 8080:8080 car-accidents-map-be

## Usage

base usage:

    java -jar path/to/car-accidents-map-fat.jar
    
To change the application parameters pass the path of a json configuration file:

    java -jar path/to/car-accidents-map-fat.jar -conf path/to/config.json

An example configuration file is in the main project folder.

The complete configuration file (with the default values) is this:

    {
        "listeningPort" : 8080,
        "dbHost" : "localhost",
        "dbPort" : 27017,
        "dbUser" : null,
        "dbPwd" : null,
        "authDB" : "admin",
        "dbName" : "infovis",
        "collectionName" : "accidents",
        "queryLimitCount" : 500
    }

- `listeningPort`: API server listening port
- `dbHost`: MongoDB instance hostname
- `dbPort`: MongoDB instance port
- `dbUser`: MongoDB user - null if unauthenticated
- `dbPwd`: MongoDB password - null if unauthenticated
- `authDB`: MongoDB authentication DB
- `dbName`: MongoDB database where the data is stored.
- `collectionName`: MongoDB collection where the data is stored.

#### Vertx options
Since this application is packaged with a Vertx launcher, all the vertx options can be passed from the command line.
For more informations see the [help page](http://vertx.io/docs/vertx-core/java/#_the_vertx_command_line)

## Feedback and contacts
If you think there is a bug, or something is missing or wrong with the documentation/support files, feel free to [open an issue].

## License
This software is released under the LGPL V3 license.
A copy is included in the LICENSE file


[open an issue]: https://github.com/marcosox/car-accidents-map-be/issues
[releases page]: https://github.com/marcosox/car-accidents-map-be/releases
