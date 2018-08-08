package ch.sbb.matsim.database;


import ch.sbb.matsim.database.tables.synpop.PersonsTable;
import org.apache.log4j.Logger;
import org.jvnet.jaxb2_commons.lang.StringUtils;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Population;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.PopulationUtils;

import java.sql.*;
import java.util.Iterator;


public class Engine {
    private final static Logger log = Logger.getLogger(Engine.class);

    private String url;
    private String user = "docker";
    private String password = System.getenv("PG_PASSWORD");

    public Engine(String schema) throws SQLException {
        this.url = "jdbc:postgresql://k13536:25432/mobi_synpop?currentSchema=" + schema;
    }

    public void dropTable(DatabaseTable table) throws SQLException {
        String sql = "DROP TABLE IF EXISTS " + table.getName() + ";";
        try (Connection connection = this.getConnection(); Statement stmt = connection.createStatement()) {
            stmt.execute(sql);
        }
    }

    public void createTable(DatabaseTable table) throws SQLException {

        try (Connection connection = this.getConnection(); Statement stmt = connection.createStatement()) {
            table.createTable(stmt);
        }
    }

    private void createSchema(String schema) throws SQLException {
        String sql = "CREATE SCHEMA IF NOT EXISTS \"" + schema + "\";";
        try (Connection connection = this.getConnection(); Statement stmt = connection.createStatement()) {
            stmt.execute(sql);
        }
    }

    private Connection getConnection() throws SQLException {

        return DriverManager.getConnection(this.url, this.user, this.password);
    }

    public void writeToTable(DatabaseTable table) throws SQLException {

        try (Connection connection = this.getConnection()) {

            String columns = StringUtils.join(table.getColumnsNames().iterator(), ",");
            String questions = StringUtils.join(table.getColumnsNames().stream().map(x -> "?").iterator(), ",");

            String sql = "insert into " + table.getName() + " (" + columns + ") values (" + questions + ")";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {

                final int batchSize = 1000;
                int count = 1;

                Iterator iter = table.getRowIterator();
                while (iter.hasNext()) {
                    table.write(iter.next(), ps);
                    ps.addBatch();

                    if (++count % batchSize == 0) {
                        ps.executeBatch();
                    }
                }
                ps.executeBatch(); // insert remaining records
            }
        }
    }

    public void readFromTable() {

    }

    public static void main(String[] args) {
        Population population = PopulationUtils.createPopulation(ConfigUtils.createConfig());

        population.addPerson(population.getFactory().createPerson(Id.createPersonId("111")));


        try {
            Engine engine = new Engine("2016test");
            PersonsTable table = new PersonsTable(population);
            engine.dropTable(table);
            engine.createTable(table);
            engine.writeToTable(table);
        } catch (SQLException a) {
            a.printStackTrace();
            log.info(a);
        }

    }
}
