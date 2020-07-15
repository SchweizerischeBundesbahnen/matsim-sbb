package ch.sbb.matsim.synpop.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.jvnet.jaxb2_commons.lang.StringUtils;
import org.matsim.core.config.ReflectiveConfigGroup;

public class AttributeMappingParameterSet extends ReflectiveConfigGroup {

	private static final String TYPE = "AttributeMapping";

	private static final String PARAM_MODE = "table";
	private static final String PARAM_COLUMN = "column";
	private static final String PARAM_FALC_COLUMNS = "FalcColumns";
	private static final String PARAM_MOBI_COLUMNS = "MobiColumns";

	private List<String> falcColumns = new ArrayList<>();
	private List<String> mobiColumns = new ArrayList<>();

	private String table;
	private String column;

	public AttributeMappingParameterSet() {
		super(TYPE);
	}

	public static String getType() {
		return TYPE;
	}

	@StringGetter(PARAM_MODE)
	public String getTable() {
		return table;
	}

	@StringSetter(PARAM_MODE)
	public void setTable(String table) {
		this.table = table;
	}

	@StringGetter(PARAM_COLUMN)
	public String getColumn() {
		return column;
	}

	@StringSetter(PARAM_COLUMN)
	public void setColumn(String table) {
		this.column = table;
	}

	@StringGetter(PARAM_FALC_COLUMNS)
	private String getFalcColumnsAsString() {
		return StringUtils.join(this.falcColumns.listIterator(), ",");
	}

	public List<String> getFalcColumns() {
		return this.falcColumns;
	}

	@StringSetter(PARAM_FALC_COLUMNS)
	private void setFalcColumns(String modes) {
		setFalcColumns(Arrays.asList(modes.split(",")));
	}

	public void setFalcColumns(List<String> modes) {
		this.falcColumns.clear();
		this.falcColumns.addAll(modes);
	}

	@StringGetter(PARAM_MOBI_COLUMNS)
	private String getMobiColumnsAsString() {
		return StringUtils.join(this.falcColumns.listIterator(), ",");
	}

	public List<String> getMobiColumns() {
		return this.mobiColumns;
	}

	@StringSetter(PARAM_MOBI_COLUMNS)
	private void setMobiColumns(String modes) {
		setMobiColumns(Arrays.asList(modes.split(",")));
	}

	public void setMobiColumns(List<String> modes) {
		this.mobiColumns.clear();
		this.mobiColumns.addAll(modes);
	}

	public String getKey() {
		return this.table + "_" + this.column;
	}
}
