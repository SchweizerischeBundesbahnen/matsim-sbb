package ch.sbb.matsim.synpop.database.tables;

import ch.sbb.matsim.database.DatabaseTable;
import ch.sbb.matsim.synpop.attributes.SynpopAttribute;
import org.matsim.facilities.ActivityFacility;

import java.util.Collection;
import java.util.Iterator;

public class FacilityTable extends DatabaseTable<ActivityFacility> {

    private final Collection<ActivityFacility> facilities;

    public FacilityTable(String name, Collection<ActivityFacility> facilities, Collection<SynpopAttribute> synpopAttributes) {
        super(name);
        this.facilities = facilities;
        this.addColumns(synpopAttributes);
    }

    private void addColumns(Collection<SynpopAttribute> synpopAttributes) {

        for (SynpopAttribute synpopAttribute : synpopAttributes) {
            this.columns.addColumn(
                    synpopAttribute.getName(),
                    synpopAttribute.getSqlType(),
                    (p, ps, i) -> ps.setObject(i, PersonTable.convertAttributeForSQL(p.getAttributes().getAttribute(synpopAttribute.getName()), synpopAttribute)),
                    (p, v) -> p.getAttributes().putAttribute(synpopAttribute.getName(), v));

        }
    }

    @Override
    public Iterator<? extends ActivityFacility> getRowIterator() {
        return this.facilities.iterator();
    }

}
