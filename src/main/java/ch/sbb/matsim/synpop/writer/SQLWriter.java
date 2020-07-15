package ch.sbb.matsim.synpop.writer;

import ch.sbb.matsim.database.Engine;
import ch.sbb.matsim.synpop.attributes.SynpopAttributes;
import ch.sbb.matsim.synpop.database.tables.FacilityTable;
import ch.sbb.matsim.synpop.database.tables.PersonTable;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Collection;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.population.Population;
import org.matsim.facilities.ActivityFacilities;
import org.matsim.facilities.ActivityFacility;

public class SQLWriter {

	private final static Logger log = Logger.getLogger(SQLWriter.class);
	private String host;
	private String port;
	private String database;
	private String schema;
	private SynpopAttributes synpopAttributes;

	public SQLWriter(String host, String port, String database, String schema, SynpopAttributes synpopAttributes) {
		this.host = host;
		this.port = port;
		this.database = database;
		this.schema = schema;
		this.synpopAttributes = synpopAttributes;
	}

	private void exportToDB(Population population) {
		try {
			Engine engine = new Engine(schema, host, port, database);
			PersonTable table = new PersonTable(population, synpopAttributes.getAttributes("persons"));
			engine.dropTable(table);
			engine.createTable(table);
			engine.writeToTable(table);
		} catch (SQLException a) {
			a.printStackTrace();
			log.info(a);
		}
	}

	private void exportToDB(Collection<ActivityFacility> facilities, String name) {
		try {
			Engine engine = new Engine(schema, host, port, database);
			FacilityTable table = new FacilityTable(name, facilities, synpopAttributes.getAttributes(name));
			engine.dropTable(table);
			engine.createTable(table);
			engine.writeToTable(table);
		} catch (SQLException a) {
			a.printStackTrace();
			log.info(a);
		}
	}

	private void writeVersion(String version) {
		try {
			Engine engine = new Engine(schema, host, port, database);
			String sql = "DROP TABLE IF EXISTS VERSION;";
			engine.executeSQL(sql);
			engine.executeSQL("CREATE TABLE VERSION (version varchar(255), username varchar(255));");
			try (PreparedStatement ps = engine.getConnection().prepareStatement("INSERT INTO VERSION (version, username) VALUES (?,?);")) {
				ps.setString(1, version);
				ps.setString(2, System.getProperty("user.name"));
				ps.executeUpdate();
			}

		} catch (SQLException a) {
			a.printStackTrace();
			log.info(a);
		}

	}

	private void makePoints(String table) {
		int ID_CH1903 = 21781;
		int ID_WGS = 4326;

		try {
			Engine engine = new Engine(host, port, database);
			//engine.executeSQL("CREATE EXTENSION postgis;");
			log.info(this.schema);
			String _table = "\"" + this.schema + "\"." + table;

			//engine.executeSQL("ALTER database " + this.database + " set search_path = \"$user\", public ;");
			engine.executeSQL("ALTER TABLE " + _table + " ADD COLUMN geo geometry(Point, " + ID_CH1903 + ");");
			engine.executeSQL("ALTER TABLE " + _table + " ADD COLUMN N_geo_wgs text;");

			engine.executeSQL("UPDATE " + _table + " SET geo = ST_SetSRID(ST_MakePoint(X,Y)," + ID_CH1903 + ");");
			engine.executeSQL("UPDATE " + _table + " SET N_geo_wgs = ST_AsText(ST_Transform(geo, " + ID_WGS + "));");
		} catch (SQLException a) {
			a.printStackTrace();
			log.info(a);
		}

	}

	public void run(Population population, ActivityFacilities facilities, String version) {

		this.exportToDB(population);
		this.exportToDB(facilities.getFacilitiesForActivityType("work").values(), "businesses");
		this.exportToDB(facilities.getFacilitiesForActivityType("home").values(), "households");
		this.writeVersion(version);
		this.makePoints("businesses");
		this.makePoints("households");

	}
}
