package io.github.marcosox.infovis;

import com.mongodb.Block;
import com.mongodb.MongoClient;
import com.mongodb.MongoCredential;
import com.mongodb.ServerAddress;
import com.mongodb.client.AggregateIterable;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.bson.Document;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


class MongoDAO {
	private String host;
	private int port;
	private String dbName;
	private String collectionName;
	private String user;
	private String authDatabase;
	private String password;
	private MongoClient client = null;

	MongoDAO(String host, int port, String dbName, String collectionName, String user, String password, String authenticationDB) {
		this.host = host;
		this.port = port;
		this.dbName = dbName;
		this.collectionName = collectionName;
		this.authDatabase = authenticationDB;
		this.user = user;
		this.password = password;
		connect();
	}

	/**
	 * Connect to MongoDB
	 */
	private void connect() {
		if (this.host != null && !this.host.trim().isEmpty() && this.port > 0 && this.port < 65536) {   // validate hostname and port
			ServerAddress address = new ServerAddress(this.host, this.port);
			if (this.user != null && this.password != null && this.authDatabase != null) {   // validate credentials
				// authenticated instance
				MongoCredential credential = MongoCredential.createCredential(this.user, this.authDatabase, this.password.toCharArray());
				this.client = new MongoClient(address, Collections.singletonList(credential));
			} else {
				// unauthenticated instance
				this.client = new MongoClient(address);
			}
		} else {
			// local unauthenticated instance
			this.client = new MongoClient();
		}
	}

	/**
	 * Disconnect from MongoDB
	 */
	void disconnect() {
		if (this.client != null) {
			this.client.close();
			this.client = null;
		}
	}

	/**
	 * Retrieve the client instance, creating a new one if needed
	 *
	 * @return the mongo client instance
	 */
	private MongoClient getClient() {
		if (this.client == null) {
			connect();
		}
		return this.client;
	}

	/**
	 * Effettua il conto dei documenti in una collezione raggruppati in base ad
	 * un campo passato come parametro.
	 *
	 * @param field campo su cui fare l'aggregazione
	 * @return un oggetto JSON contenente un array di oggetti ognuno con campi
	 * _id e count
	 */
	JsonArray getCount(String field, int limit) {
		JsonArray result = new JsonArray();
		MongoDatabase db = getClient().getDatabase(this.dbName);
		MongoCollection<Document> collection = db.getCollection(this.collectionName);
		AggregateIterable<Document> iterable;

		if (field == null || field.isEmpty()) {
			field = "anno"; // default
		}

		List<Document> list = new ArrayList<>();
		list.add(new Document("$project", new Document("field", "$" + field)));
		if (field.contains(".")) {
			list.add(new Document("$unwind", "$field"));
		}
		list.add(new Document("$group", new Document("_id", "$field").append("count", new Document("$sum", 1))));

		list.add(new Document("$sort", new Document("count", -1)));
		if (limit > 0) {
			list.add(new Document("$limit", limit));
		}

		iterable = collection.aggregate(list);
		iterable.forEach((Block<Document>) result::add);

		return result;
	}

	/**
	 * Conta i totali dei documenti presenti nelle collezioni veicoli, incidenti, persone.
	 *
	 * @return un array di oggetti JSON con due campi: collezione e totale, per ogni collezione.
	 */
	JsonObject getTotals() {
		MongoDatabase db = getClient().getDatabase(this.dbName);
		MongoCollection<Document> incidenti = db.getCollection(this.collectionName);
		JsonObject risultato = new JsonObject();
		risultato.put("incidenti", incidenti.count());

		// itera direttamente su queste collezioni e per ognuna conta il totale
		String[] collectionsList = {"veicoli", "persone"};

		for (String s : collectionsList) {
			List<Document> pipeline = new ArrayList<>();
			pipeline.add(new Document("$unwind", "$" + s));
			pipeline.add(new Document("$group", new Document("_id", "null").append("count", new Document("$sum", 1))));
			AggregateIterable<Document> result = incidenti.aggregate(pipeline);
			risultato.put(s, result.first().getInteger("count"));
		}
		risultato.put("strade", this.getCount("strada", -1).size());
		return risultato;
	}

	/**
	 * Recupera la collezione con le coordinate dei municipi
	 *
	 * @return Oggetto JSON con l'intera collezione di mongo
	 */
	JsonArray getDistricts() {
		JsonArray result = new JsonArray();
		MongoCollection<Document> collection = getClient().getDatabase(this.dbName).getCollection("municipi");
		FindIterable<Document> iterable = collection.find();
		iterable.forEach((Block<Document>) d -> {
			JsonObject municipio = new JsonObject();
			municipio.put("coord", d.getString("coord"));
			municipio.put("name", d.getString("name"));
			municipio.put("numero", String.valueOf(d.getInteger("numero")));
			municipio.put("description", d.getString("description"));
			result.add(municipio);
		});
		return result;
	}

	/**
	 * recupera un documento dal db
	 *
	 * @param id id incidente
	 * @return il documento dell'incidente o un documento vuoto
	 */
	String getAccidentDetails(int id) {
		MongoCollection<Document> collection = getClient().getDatabase(this.dbName).getCollection(this.collectionName);
		FindIterable<Document> iterable = collection.find(new Document("incidente", "incidente" + id));
		return iterable.first() == null ? new Document().toJson() : iterable.first().toJson();
	}

	/**
	 * Recupera tutti gli incidenti e li ritorna in una lista per visualizzarli sulla mappa
	 *
	 * @return una lista di oggetti {lat,lon,protocollo}
	 */
	JsonArray getAccidents() {
		MongoCollection<Document> collection = getClient().getDatabase(this.dbName).getCollection(this.collectionName);
		FindIterable<Document> iterable = collection.find();
		JsonArray result = new JsonArray();
		iterable.forEach((Block<Document>) d -> {
			JsonObject mappa = new JsonObject();
			if (d.getString("lat") != null) {
				mappa.put("lat", d.getString("lat"));
				mappa.put("lon", d.getString("lon"));
				mappa.put("anno", d.getString("anno"));
				mappa.put("numero_gruppo", "" + d.getInteger("numero_gruppo"));
				mappa.put("ora", "" + d.getInteger("ora"));
				mappa.put("protocollo", d.getString("incidente").replace("incidente", ""));
				result.add(mappa);
			}
		});
		return result;
	}

	/**
	 * Funzione di utilita' per la visualizzazione sulla mappa degli incidenti
	 *
	 * @param anno   anno da filtrare, se null e' ignorato
	 * @param mese   mese da filtrare, se null e' ignorato
	 * @param giorno giorno da filtrare, se null e' ignorato
	 * @param ora    ora da filtrare, se null e' ignorata
	 * @return array di JSON con 3 campi: numero municipio, numero incidenti nel municipio, totale incidenti.
	 */
	JsonArray getIncidentiMunicipi(String anno, String mese, String giorno, String ora) {
		MongoCollection<Document> collection = getClient().getDatabase(this.dbName).getCollection(this.collectionName);
		Document matchFilter = new Document();    // filtro per mese anno, giorno e ora
		List<Document> aggregationPipeline = new ArrayList<>();

		if (anno != null && !anno.isEmpty()) {
			matchFilter.append("anno", anno);
		}
		if (mese != null && !mese.isEmpty()) {
			matchFilter.append("mese", mese);
		}
		if (giorno != null && !giorno.isEmpty()) {
			matchFilter.append("giorno", giorno);
		}
		if (ora != null && !ora.isEmpty()) {
			matchFilter.append("ora", Integer.valueOf(ora));    // ora e' un intero, mese giorno e anno sono stringhe
		}

		final long incidenti = collection.count(matchFilter);    // conta gli incidenti con filtro
		Document match = new Document("$match", matchFilter);    // includi il filtro in uno stage match della pipeline
		aggregationPipeline.add(match);
		aggregationPipeline.add(new Document(
				"$group", new Document("_id", "$numero_gruppo")
				.append("count", new Document("$sum", 1)
				)
		));
		AggregateIterable<Document> iterable = collection.aggregate(aggregationPipeline);

		JsonArray result = new JsonArray();
		iterable.forEach((Block<Document>) d -> {
			Document dc = new Document();
			dc.append("municipio", d.getInteger("_id"));
			dc.append("incidenti", d.getInteger("count"));
			dc.append("totale", incidenti);     // TODO: remove this
			result.add(dc);
		});
		return result;
	}

	/**
	 * Calcola il totale giornaliero degli incidenti per la visualizzazione sul calendario
	 *
	 * @return un array json con oggetti di tipo <data,totale>
	 */
	JsonArray getAccidentsByDay() {
		MongoCollection<Document> collection = getClient().getDatabase(this.dbName).getCollection(this.collectionName);
		List<Document> aggregationPipeline = new ArrayList<>();
		aggregationPipeline.add(
				new Document("$group",
						new Document("_id",
								new Document("anno", "$anno").append("mese", "$mese").append("giorno", "$giorno"))
								.append("totale", new Document("$sum", 1))));
		aggregationPipeline.add(
				new Document("$group", new Document("tot", new Document("$push", new Document("total", "$totale"))).append("_id", "$_id")));

		AggregateIterable<Document> iterable = collection.aggregate(aggregationPipeline);
		JsonArray result = new JsonArray();
		iterable.forEach((Block<Document>) d -> {
			Document dc = new Document();
			String anno = d.get("_id", Document.class).getString("anno");
			String mese = d.get("_id", Document.class).getString("mese");
			String giorno = d.get("_id", Document.class).getString("giorno");
			@SuppressWarnings("unchecked")
			ArrayList<Document> lista = d.get("tot", ArrayList.class);    // necessario cast unchecked
			int count = lista.get(0).getInteger("total");
			String data = anno + "-" + mese + "-" + giorno;
			dc.append("data", data);
			dc.append("count", count);
			result.add(dc);
		});
		return result;
	}

	/**
	 * Effettua il conto dei documenti raggruppati in base ad un campo passato come parametro.
	 * Per ogni valore riporta il totale relativo di un altro campo passato come parametro.
	 * es: riporta il conto di ogni veicolo nel authDatabase, e per ogni veicolo
	 * riporta quanti incidenti in una certa via
	 * TODO: anziche' fare la seconda query, filtrare il risultato della prima
	 *
	 * @param field          campo su cui fare l'aggregazione
	 * @param highlightField campo del sottovalore da riportare
	 * @param highlightValue valore su cui filtrare il sottovalore
	 * @return un oggetto JSON contenente un array di documenti ognuno con campi
	 * _id, count, highlight
	 */
	JsonArray getAggregateCount(String field, int limit, String highlightField, String highlightValue, boolean sortDescending) {

		String countFieldName = "count";
		String highlightFieldName = "highlight";

		MongoDatabase db = getClient().getDatabase(this.dbName);
		MongoCollection<Document> collection = db.getCollection(this.collectionName);

		if (field == null || field.isEmpty()) {
			field = "anno"; // default
		}

		List<Document> list = new ArrayList<>();
		list.add(new Document("$project", new Document("field", "$" + field)));
		if (field.contains(".")) {
			list.add(new Document("$unwind", "$field"));
		}
		list.add(new Document("$group", new Document("_id", "$field").append(countFieldName, new Document("$sum", 1))));
		list.add(new Document("$sort", new Document("count", sortDescending ? -1 : 1)));
		list.add(new Document("$limit", limit));

		List<Document> listWithMatch = new ArrayList<>();
		if (highlightField != null && !highlightField.isEmpty() && highlightValue != null && !highlightValue.isEmpty()) {
			listWithMatch.add(new Document("$match", new Document(highlightField, highlightValue)));
		}
		listWithMatch.add(new Document("$project", new Document("field", "$" + field)));
		if (field.contains(".")) {
			listWithMatch.add(new Document("$unwind", "$field"));
		}
		listWithMatch.add(new Document("$group", new Document("_id", "$field").append(countFieldName, new Document("$sum", 1))));
		listWithMatch.add(new Document("$sort", new Document("count", sortDescending ? -1 : 1)));
		listWithMatch.add(new Document("$limit", limit));

		AggregateIterable<Document> countIterable = collection.aggregate(list);
		AggregateIterable<Document> highlightIterable = collection.aggregate(listWithMatch);

		JsonArray result = new JsonArray();
		JsonObject highlightCounts = new JsonObject();

		highlightIterable.forEach((Block<Document>) document -> highlightCounts.put(
				String.valueOf(document.getOrDefault("_id", "null")),
				document.getOrDefault(countFieldName, 0)));

		countIterable.forEach((Block<Document>) document -> {
			String id = String.valueOf(document.getOrDefault("_id", "null"));
			int highlightCount = highlightCounts.getInteger(id, 0);
			JsonObject entry = new JsonObject();
			entry.put("_id", id);
			entry.put(countFieldName, document.getInteger(countFieldName) - highlightCount);    // subtract highlights from count
			entry.put(highlightFieldName, highlightCount);
			result.add(entry);
		});

		return result;
	}
}
