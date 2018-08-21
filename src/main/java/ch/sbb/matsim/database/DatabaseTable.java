package ch.sbb.matsim.database;


import java.sql.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;


public abstract class DatabaseTable<T> {

    private String name;

    public interface DatabaseRowGetter<T> {
        void get(T object, PreparedStatement ps, int i) throws SQLException;
    }

    public interface DatabaseRowSetter<T> {
        void set(T object, Object value);
    }


    protected final DatabaseColumns<T> columns;


    public DatabaseTable(String name) {
        this.name = name;
        this.columns = new DatabaseColumns<>();
    }

    public String getName() {
        return this.name;
    }

    public void write(T object, PreparedStatement ps) throws SQLException {
        int i = 1;
        for (DatabaseColumn column : this.columns.columns) {
            column.getter.get(object, ps, i);
            i++;
        }

    }

    public void createTable(Statement stmt) throws SQLException {


        String sql = "CREATE TABLE " + this.getName() + " (";
        boolean first = true;
        for (DatabaseColumn column : this.columns.columns) {
            if (!first) sql += ",";
            sql += column.name + " " + column.sqlType;
            first = false;
        }

        stmt.execute(sql + ");");
    }

    public List<String> getColumnsNames() {
        return columns.columns.stream().map(c -> c.name).collect(Collectors.toList());
    }


    public abstract Iterator<? extends T> getRowIterator();

    public class DatabaseColumns<T> {
        List<DatabaseColumn> columns = new ArrayList<>();

        public void addColumn(String name, String sqlType, DatabaseRowGetter<T> getter, DatabaseRowSetter<T> setter) {

            this.columns.add(new DatabaseColumn<T>(name, sqlType, getter, setter));
        }
    }

    public class DatabaseColumn<T> {


        String sqlType;
        String name;
        DatabaseRowGetter<T> getter;
        DatabaseRowSetter<T> setter;

        public DatabaseColumn(String name, String sqlType, DatabaseRowGetter<T> getter, DatabaseRowSetter<T> setter) {
            this.name = name;
            this.sqlType = sqlType;
            this.getter = getter;
            this.setter = setter;
        }
    }


}
