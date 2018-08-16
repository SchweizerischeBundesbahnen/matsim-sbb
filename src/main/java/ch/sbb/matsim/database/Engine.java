package ch.sbb.matsim.database;

import org.apache.log4j.Logger;
import org.jvnet.jaxb2_commons.lang.StringUtils;

import java.sql.*;
import java.util.Iterator;


public class Engine {
    private final static Logger log = Logger.getLogger(Engine.class);

    private String url;
    private String user = "docker";
    private String password = System.getenv("PG_PASSWORD");

    public Engine(String schema, String host, String port, String database) {
        this.url = "jdbc:postgresql://" + host + ":" + port + "/" + database + "?currentSchema=" + schema;
    }

    public Engine(String host, String port, String database) {
        this.url = "jdbc:postgresql://" + host + ":" + port + "/" + database;
    }

    public void dropTable(DatabaseTable table) throws SQLException {
        String sql = "DROP TABLE IF EXISTS " + table.getName() + ";";
        this.executeSQL(sql);
    }

    public void executeSQL(String sql) throws SQLException {

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
        this.executeSQL(sql);
    }

    public Connection getConnection() throws SQLException {

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

}
