/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2017.
 */

package ch.sbb.matsim.synpop.config;

import org.matsim.core.config.ConfigGroup;
import org.matsim.core.config.ReflectiveConfigGroup;

import java.util.HashMap;
import java.util.Map;


public class SynpopConfigGroup extends ReflectiveConfigGroup {

    static public final String GROUP_NAME = "SBBSynpop";

    private String falcFolder = "";
    private String outputFolder = "./output_synpop";
    private String zoneShapefile = "";
    private String host = "k13536";
    private String port = "25432";
    private String database = "mobi_synpop";
    private String year = "2016test";
    private String attributesCSV = "";
    private String version = "v1";
    private String bus2act = "";


    private final Map<String, ColumnMappingParameterSet> columnMappingPerTable = new HashMap<>();
    private final Map<String, Map<String, AttributeMappingParameterSet>> attributMappingPerColumn = new HashMap<>();


    public SynpopConfigGroup() {
        super(GROUP_NAME);
    }

    @Override
    public ConfigGroup createParameterSet(String type) {
        if (ColumnMappingParameterSet.getType().equals(type)) {
            return new ColumnMappingParameterSet();
        } else if (AttributeMappingParameterSet.getType().equals(type)) {
            return new AttributeMappingParameterSet();
        } else {
            throw new IllegalArgumentException("Unsupported parameterset-type: " + type);
        }
    }

    @Override
    public void addParameterSet(ConfigGroup set) {
        if (set instanceof ColumnMappingParameterSet) {
            addColumMappingSetting((ColumnMappingParameterSet) set);
        } else if (set instanceof AttributeMappingParameterSet) {
            addAttributeMappingSettings((AttributeMappingParameterSet) set);
        } else {
            throw new IllegalArgumentException("Unsupported parameterset: " + set.getClass().getName());
        }
    }

    public void addColumMappingSetting(ColumnMappingParameterSet settings) {
        this.columnMappingPerTable.put(settings.getTable(), settings);
        super.addParameterSet(settings);
    }


    public void addAttributeMappingSettings(AttributeMappingParameterSet settings) {
        String table = settings.getTable();

        this.attributMappingPerColumn.putIfAbsent(table, new HashMap<>());

        Map<String, AttributeMappingParameterSet> a = this.attributMappingPerColumn.get(table);
        a.put(settings.getColumn(), settings);

        super.addParameterSet(settings);
    }

    public Map<String, ColumnMappingParameterSet> getColumnMappingSettings() {
        return this.columnMappingPerTable;
    }


    public Map<String, Map<String, AttributeMappingParameterSet>> getAttributeMappingSettings() {
        return this.attributMappingPerColumn;
    }


    @StringGetter("bus2act")
    public String getBus2act() {
        return this.bus2act;
    }

    @StringSetter("bus2act")
    public void setBus2act(String path) {
        this.bus2act = path;
    }


    @StringGetter("host")
    public String getHost() {
        return host;
    }

    @StringSetter("host")
    public void setHost(String host) {
        this.host = host;
    }

    @StringGetter("version")
    public String getVersion() {
        return version;
    }

    @StringSetter("version")
    public void setVersion(String version) {
        this.version = version;
    }

    @StringGetter("port")
    public String getPort() {
        return port;
    }

    @StringSetter("port")
    public void setPort(String port) {
        this.port = port;
    }

    @StringGetter("database")
    public String getDatabase() {
        return database;
    }

    @StringSetter("database")
    public void setDatabase(String database) {
        this.database = database;
    }

    @StringGetter("year")
    public String getYear() {
        return year;
    }

    @StringSetter("year")
    public void setYear(String year) {
        this.year = year;
    }


    @StringGetter("attributesCSV")
    public String getAttributesCSV() {
        return attributesCSV;
    }

    @StringSetter("attributesCSV")
    void setAttributesCSV(String file) {
        this.attributesCSV = file;
    }

    @StringGetter("falcFolder")
    public String getFalcFolder() {
        return falcFolder;
    }

    @StringSetter("falcFolder")
    void setFalcFolder(String file) {
        this.falcFolder = file;
    }

    @StringGetter("zoneShapefile")
    public String getZoneShapefile() {
        return zoneShapefile;
    }

    @StringSetter("zoneShapefile")
    public void setZoneShapefile(String zoneShapefile) {
        this.zoneShapefile = zoneShapefile;
    }

    @StringGetter("outputFolder")
    public String getOutputFolder() {
        return outputFolder;
    }

    @StringSetter("outputFolder")
    public void setOutputFolder(String outputFolder) {
        this.outputFolder = outputFolder;
    }


}
