package ch.sbb.matsim.synpop.converter;

import ch.sbb.matsim.synpop.config.AttributeMappingParameterSet;
import ch.sbb.matsim.synpop.config.ColumnMappingParameterSet;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Population;
import org.matsim.facilities.ActivityFacility;

public class AttributesConverter {

	private final static Logger log = Logger.getLogger(AttributeMapper.class);
	private Map<String, Map<String, AttributeMappingParameterSet>> attributeMapping;
	private Map<String, ColumnMappingParameterSet> columnMapping;
	private Integer baseYear;

	public AttributesConverter(Map<String, Map<String, AttributeMappingParameterSet>> attributeMapping,
			Map<String, ColumnMappingParameterSet> columnMapping, Integer baseYear) {
		this.attributeMapping = attributeMapping;
		this.columnMapping = columnMapping;
		this.baseYear = baseYear;
	}

	private void mapColumns(Map<String, ColumnMappingParameterSet> columnsMapping, String table, ColumnMapper mapper) {

		if (columnsMapping.containsKey(table)) {
			ColumnMappingParameterSet settings = columnsMapping.get(table);

			Iterator<String> falcIterator = settings.getFalcColumns().iterator();
			Iterator<String> mobiIterator = settings.getMobiColumns().iterator();

			while (falcIterator.hasNext() && mobiIterator.hasNext()) {

				String falcColumn = falcIterator.next();
				String mobiColumn = mobiIterator.next();

				log.info("Changing column Name " + falcColumn + "->" + mobiColumn + "(" + table + ")");
				mapper.change(falcColumn, mobiColumn);
			}
		}
	}

	private void mapAttributes(Map<String, Map<String, AttributeMappingParameterSet>> attributesMapping, String table, AttributeMapper mapper) {
		if (attributesMapping.containsKey(table)) {
			for (String column : attributesMapping.get(table).keySet()) {
				AttributeMappingParameterSet settings = attributesMapping.get(table).get(column);
				HashMap<String, String> map = new HashMap<>();
				Iterator<String> falcIterator = settings.getFalcColumns().iterator();
				Iterator<String> mobiIterator = settings.getMobiColumns().iterator();
				while (falcIterator.hasNext() && mobiIterator.hasNext()) {
					String falcAttribute = falcIterator.next();
					String mobiAttribute = mobiIterator.next();
					map.put(falcAttribute, mobiAttribute);
				}

				log.info(column);
				mapper.change(map, column);
			}
		}
	}

	public void map(Population population) {
		String table = "persons";
		mapColumns(this.columnMapping, table, (o, n) -> {
			population.getPersons().values().stream().forEach(p -> {
				Object attribute = p.getAttributes().getAttribute(o);
				p.getAttributes().removeAttribute(o);
				p.getAttributes().putAttribute(n, attribute);

			});
		});

		mapAttributes(this.attributeMapping, table, (map, column) -> {
			population.getPersons().values().forEach(
					person -> {
						String value = person.getAttributes().getAttribute(column).toString();

						if (!map.containsKey(value)) {
							log.error(value + " not defined for column " + column);
						}
						person.getAttributes().putAttribute("N_" + column, map.get(value));
					}
			);
		});

		PersonAttributes personAttributes = new PersonAttributes(this.baseYear);
		for (Person person : population.getPersons().values()) {
			personAttributes.completeAttributes(person);
			person.getAttributes();
		}

	}

	public void map(Collection<ActivityFacility> facilities, String table) {

		mapColumns(this.columnMapping, table, (o, n) -> {
			facilities.forEach(h -> {
				Object attribute = h.getAttributes().getAttribute(o);
				h.getAttributes().removeAttribute(o);
				h.getAttributes().putAttribute(n, attribute);
			});
		});

		mapAttributes(this.attributeMapping, table, (map, column) -> {
			facilities.forEach(f -> {
						String value = f.getAttributes().getAttribute(column).toString();
						if (!map.containsKey(value)) {
							log.error(value + " not defined for column " + column);
						}
						f.getAttributes().putAttribute("N_" + column, map.get(value));
					}
			);
		});

	}

	public interface ColumnMapper {

		void change(String oldColumn, String newColumn);
	}

	public interface AttributeMapper {

		void change(Map<String, String> map, String column);
	}
}
