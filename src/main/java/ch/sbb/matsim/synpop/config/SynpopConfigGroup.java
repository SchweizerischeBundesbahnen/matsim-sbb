/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2017.
 */

package ch.sbb.matsim.synpop.config;

import java.util.HashMap;
import java.util.Map;
import org.matsim.core.config.ConfigGroup;
import org.matsim.core.config.ReflectiveConfigGroup;

public class SynpopConfigGroup extends ReflectiveConfigGroup {

	static public final String GROUP_NAME = "SBBSynpop";
	private final Map<String, ColumnMappingParameterSet> columnMappingPerTable = new HashMap<>();
	private final Map<String, Map<String, AttributeMappingParameterSet>> attributMappingPerColumn = new HashMap<>();
	private String falcFolder = "";
	private String outputFolder = "./output_synpop";
	private String zoneShapefile = "";
	private String shapeAttribute = "";
	private String host = "k13536";
	private String port = "25432";
	private String database = "mobi_synpop";
	private int baseYear = 2016;
	private String attributesCSV = "";
	private String version = "v1";

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
	public Integer getBaseYear() {
		return baseYear;
	}

	@StringSetter("year")
	public void setBaseYear(int year) {
		this.baseYear = year;
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

	@StringGetter("shapeAttribute")
	public String getShapeAttribute() {
		return shapeAttribute;
	}

	@StringSetter("shapeAttribute")
	public void setShapeAttribute(String shapeAttribute) {
		this.shapeAttribute = shapeAttribute;
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
