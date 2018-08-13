package ch.sbb.matsim.database.tables.synpop;

import ch.sbb.matsim.database.DatabaseTable;
import ch.sbb.matsim.synpop.attributes.PersonAttributes;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Population;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;

public class PersonsTable extends DatabaseTable<Person> {


    private final Population population;

    public PersonsTable(Population population) throws SQLException {
        super("persons");
        this.population = population;
        this.columns.addColumn(
                PersonAttributes.COL_PERSON_ID, "VARCHAR NOT NULL",
                (p, ps, i) -> ps.setString(i, p.getId().toString()),
                (p, v) -> p.getAttributes().putAttribute(PersonAttributes.COL_PERSON_ID, v));

        ArrayList<String> columns = new ArrayList<>(Arrays.asList(
                PersonAttributes.COL_POSITION_IN_BUS,
                PersonAttributes.COL_POSITION_IN_EDU,
                PersonAttributes.COL_POSITION_IN_HH,
                PersonAttributes.COL_LEVEL_OF_EMPLOYMENT,
                PersonAttributes.COL_OWNERSHIP,
                PersonAttributes.COL_EDUCATION,
                PersonAttributes.COL_SEX,
                PersonAttributes.COL_INCOME,
                PersonAttributes.COL_LANGUAGE,
                PersonAttributes.COL_MOBILITY,
                PersonAttributes.COL_NUMBER_CAR,
                PersonAttributes.COL_AGE,
                PersonAttributes.COL_SECTOR));
        columns.forEach(column ->
                this.columns.addColumn(column, "INT NOT NULL",
                        (p, ps, i) -> ps.setInt(i, Integer.valueOf(p.getAttributes().getAttribute(column).toString())),
                        (p, v) -> p.getAttributes().putAttribute(column, v))
        );

        columns = new ArrayList<>(Arrays.asList(
                PersonAttributes.COL_HOUSEHOLD_ID,
                PersonAttributes.COL_BUSINESS_ID,
                "N_" + PersonAttributes.COL_POSITION_IN_BUS,
                "N_" + PersonAttributes.COL_POSITION_IN_EDU,
                "N_" + PersonAttributes.COL_POSITION_IN_HH,
                "N_" + PersonAttributes.COL_OWNERSHIP,
                "N_" + PersonAttributes.COL_SEX,
                "N_" + PersonAttributes.COL_LANGUAGE,
                "N_" + PersonAttributes.COL_MOBILITY,
                "N_"+PersonAttributes.COL_EDUCATION,
                "N_" + PersonAttributes.COL_SECTOR));
        columns.forEach(column ->
                this.columns.addColumn(column, "VARCHAR NOT NULL",
                        (p, ps, i) -> ps.setString(i, (p.getAttributes().getAttribute(column).toString())),
                        (p, v) -> p.getAttributes().putAttribute(column, v))
        );



        columns = new ArrayList<>(Arrays.asList(
                PersonAttributes.COL_CAR_AVAIL,
                PersonAttributes.COL_GA,
                PersonAttributes.COL_HTA,
                PersonAttributes.COL_VA,
                PersonAttributes.COL_HAS_CHILD
        ));
        columns.forEach(column ->
                this.columns.addColumn(column, "BOOLEAN NOT NULL",
                        (p, ps, i) -> ps.setBoolean(i, Boolean.valueOf(p.getAttributes().getAttribute(column).toString())),
                        (p, v) -> p.getAttributes().putAttribute(column, v))
        );


    }

    @Override
    public Iterator<? extends Person> getRowIterator() {
        return this.population.getPersons().values().iterator();
    }

}
