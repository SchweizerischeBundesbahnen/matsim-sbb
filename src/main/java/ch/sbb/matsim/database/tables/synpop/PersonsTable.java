package ch.sbb.matsim.database.tables.synpop;

import ch.sbb.matsim.database.DatabaseTable;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Population;

import java.sql.SQLException;
import java.util.Iterator;

public class PersonsTable extends DatabaseTable<Person> {

    private static final String PERSON_ID = "person_id";

    private final Population population;


    public PersonsTable(Population population) throws SQLException {
        super("persons");
        this.population = population;
        this.columns.addColumn(
                PERSON_ID, "VARCHAR NOT NULL", (p, ps, i) -> ps.setString(i, p.getId().toString()), (p, v) -> p.getAttributes().putAttribute(PERSON_ID, v));
    }

    @Override
    public Iterator<? extends Person> getRowIterator() {
        return this.population.getPersons().values().iterator();
    }

}
