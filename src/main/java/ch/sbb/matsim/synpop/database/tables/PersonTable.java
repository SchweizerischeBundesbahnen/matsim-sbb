package ch.sbb.matsim.synpop.database.tables;

import ch.sbb.matsim.database.DatabaseTable;
import ch.sbb.matsim.synpop.attributes.SynpopAttribute;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Population;

import java.util.Collection;
import java.util.Iterator;

public class PersonTable extends DatabaseTable<Person> {

    private final Population population;

    public PersonTable(Population population, Collection<SynpopAttribute> synpopAttributes) {
        super("persons");
        this.population = population;
        this.addColumns(synpopAttributes);
    }


    private void addColumns(Collection<SynpopAttribute> synpopAttributes) {

        for (SynpopAttribute synpopAttribute : synpopAttributes) {
            this.columns.addColumn(
                    synpopAttribute.getName(),
                    synpopAttribute.getSqlType(),
                    (p, ps, i) -> ps.setObject(i, convertAttributeForSQL(p.getAttributes().getAttribute(synpopAttribute.getName()), synpopAttribute)),
                    (p, v) -> p.getAttributes().putAttribute(synpopAttribute.getName(), v));

        }
    }

    @Override
    public Iterator<? extends Person> getRowIterator() {
        return this.population.getPersons().values().iterator();
    }


    public static Object convertAttributeForSQL(Object value, SynpopAttribute synpopAttribute) {

        if (synpopAttribute.getSqlType().contains("BOOLEAN")) {
            return value;
        } else if (synpopAttribute.getSqlType().contains("VARCHAR")) {
            return value;
        } else if (synpopAttribute.getSqlType().contains("INT")) {
            return Integer.valueOf(value.toString());
        } else if (synpopAttribute.getSqlType().contains("FLOAT")) {
            return Float.valueOf(value.toString());
        } else {
            System.exit(-1);
            return null;
        }
    }

}
