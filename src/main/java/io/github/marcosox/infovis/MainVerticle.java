package io.github.marcosox.infovis;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.Json;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.CorsHandler;

import java.util.List;

public class MainVerticle extends AbstractVerticle {
	private static final String APP_NAME = "Car accidents map - backend";
	private final String APP_VERSION = getClass().getPackage().getSpecificationVersion();

	// local vars
	private int listeningPort;
	private Integer limitCount;
	private MongoDAO dao;

	/**
	 * Main entry point
	 *
	 * @param fut Vert.x Future object
	 */
	@Override
	public void start(Future<Void> fut) {
		System.out.println("Welcome to " + APP_NAME + " version " + APP_VERSION);
		setup();
		Router router = Router.router(vertx);
		router.route().handler(BodyHandler.create());
		router.route().handler(CorsHandler.create("*")
				.allowedMethod(HttpMethod.GET)
				.allowedHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN.toString())
				.allowedHeader(HttpHeaders.CONTENT_TYPE.toString())
				.allowedHeader(HttpHeaders.ORIGIN.toString()));

		router.get("/GetTotal").handler(r -> r.response().putHeader("content-type", "application/json").end(dao.getTotals()));
		router.get("/Municipi").handler(r -> r.response().putHeader("content-type", "application/json").end(dao.getMunicipi()));
		router.get("/GetDailyAccidents").handler(r -> r.response().putHeader("content-type", "application/json").end(dao.getAccidentsByDay()));
		router.get("/GetGeocodedAccidents").handler(r -> r.response().putHeader("content-type", "application/json").end(dao.getIncidenti()));
		router.get("/GetAccidentDetails").handler(this::handleAccidentDetail);
		router.get("/GetCountWithHighlight").handler(this::handleCountWithHighLights);
		router.get("/GetCount").handler(this::handleCount);
		router.get("/GetIncidentiMunicipi").handler(this::handleGetIncidentiMunicipi);
		router.get("/shutdown").handler(this::quit);
		router.get("/").handler(r -> this.handleRootURL(r, router.getRoutes()));

		vertx.createHttpServer().requestHandler(router::accept).listen(listeningPort);
		System.out.println("HTTP server ready and listening on port " + listeningPort);
	}

	/**
	 * Quits the application
	 *
	 * @param routingContext http request routing context
	 */
	private void quit(RoutingContext routingContext) {
		dao.disconnect();
		routingContext.response().putHeader("content-type", "text/plain").end("BYE");
		vertx.close();
		System.exit(0);
	}

	/**
	 * Root url handler
	 *
	 * @param routingContext http request routing context
	 * @param routes         list of routes to display
	 */
	private void handleRootURL(RoutingContext routingContext, List<Route> routes) {
		StringBuilder routesList = new StringBuilder();
		routesList.append("<ul>");
		for (Route r : routes) {
			if (r.getPath() != null) {
				routesList.append("<li><a href='").append(r.getPath()).append("'>").append(r.getPath()).append("</a></li>");
			}
		}
		routesList.append("</ul>");
		routingContext.response().putHeader("content-type", "text/html").end(routesList.toString());
	}

	/**
	 * Handler
	 *
	 * @param r http request routing context
	 */
	private void handleAccidentDetail(RoutingContext r) {
		String id = r.request().getParam("id");
		if (id != null) {
			Object item = dao.getAccidentDetails(Integer.parseInt(id));
			if (item != null) {
				r.response().putHeader("content-type", "application/json").end(Json.encodePrettily(item));
			} else {
				r.response().setStatusCode(404).end("item " + id + " not found");
			}
		}
	}

	/**
	 * Handler
	 *
	 * @param r http request routing context
	 */
	private void handleCount(RoutingContext r) {
		String fieldName = r.request().getParam("field");
		int n = getInt(r.request().getParam("limit"), limitCount);
		r.response().putHeader("content-type", "application/json").end(dao.getCount(fieldName, n));
	}

	/**
	 * @param param        parameter
	 * @param defaultValue default value if parameter is empty or null
	 * @return param parsed as an int or the default value
	 */
	private int getInt(String param, int defaultValue) {
		int n = defaultValue;
		if (param != null && !param.trim().isEmpty()) {
			try {
				n = Integer.parseInt(param);
			} catch (NumberFormatException e) {
				System.out.println("Error: Number Format Exception for LIMIT parameter, ignoring");
			}
		}
		return n;
	}

	/**
	 * Handler
	 *
	 * @param r http request routing context
	 */
	private void handleGetIncidentiMunicipi(RoutingContext r) {
		String anno = r.request().getParam("anno");
		String mese = r.request().getParam("mese");
		String giorno = r.request().getParam("giorno");
		String ora = r.request().getParam("ora");

		System.out.println("anno: "+anno + "\tMese: " + mese + "\tGiorno: " + giorno + "\tOra: " + ora);

		r.response().putHeader("content-type", "application/json").end(dao.getIncidentiMunicipi(anno, mese, giorno, ora));
	}

	/**
	 * Handler
	 *
	 * @param r http request routing context
	 */
	private void handleCountWithHighLights(RoutingContext r) {
		String fieldName = r.request().getParam("field");
		String highlightField = r.request().getParam("highlight-field");
		String highlightValue = r.request().getParam("highlight-value");
		int limit = getInt(r.request().getParam("limit"), 20);
		boolean sortDescending = !("asc".equals(r.request().getParam("sort")));
		r.response().putHeader("content-type", "application/json")
				.end(dao.getAggregateCount(fieldName, limit, highlightField, highlightValue, sortDescending));
	}

	/**
	 * Application setup
	 */
	private void setup() {
		this.listeningPort = config().getInteger("listeningPort", ConfigurationConstants.DEFAULT_PORT);
		String dbHost = Vertx.currentContext().config().getString("dbHost", ConfigurationConstants.DEFAULT_DB_HOST);
		int dbPort = Vertx.currentContext().config().getInteger("dbPort", ConfigurationConstants.DEFAULT_DB_PORT);
		String dbUser = Vertx.currentContext().config().getString("dbUser", null);
		String dbPwd = Vertx.currentContext().config().getString("dbPwd", null);
		String authDB = Vertx.currentContext().config().getString("authDB", ConfigurationConstants.DEFAULT_AUTH_DB);
		String dbName = Vertx.currentContext().config().getString("dbName", ConfigurationConstants.DEFAULT_DB_NAME);
		String collectionName = Vertx.currentContext().config().getString("collectionName", ConfigurationConstants.DEFAULT_COLLECTION_NAME);
		this.limitCount = Vertx.currentContext().config().getInteger("queryLimitCount", ConfigurationConstants.DEFAULT_RESULT_LIMIT);
		this.dao = new MongoDAO(dbHost, dbPort, dbName, collectionName, dbUser, dbPwd, authDB);
	}
}
