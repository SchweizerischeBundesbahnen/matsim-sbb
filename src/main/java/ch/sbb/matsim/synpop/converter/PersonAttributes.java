package ch.sbb.matsim.synpop.converter;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.population.Person;

import java.util.Arrays;

public class PersonAttributes {
    private final static Logger log = Logger.getLogger(PersonAttributes.class);

    private final static String COL_MOBILITY = "mobility";
    private final static String COL_AGE = "age";
    private final static String COL_HAS_CHILD = "has_child";
    private final static String COL_GA = "GA";
    private final static String COL_HTA = "HTA";
    private final static String COL_VA = "VA";
    private final static String COL_CAR_AVAIL = "car_avail";



    private boolean carAvail(Person person) {
        return Arrays.asList("10", "11", "12", "15", "16").contains(person.getAttributes().getAttribute(COL_MOBILITY));
    }

    private boolean hasGA(Person person) {
        return Arrays.asList("2", "12").contains(person.getAttributes().getAttribute(COL_MOBILITY));
    }

    private boolean hasHTA(Person person) {
        return Arrays.asList("1", "6", "11", "16").contains(person.getAttributes().getAttribute(COL_MOBILITY));
    }

    private boolean hasVA(Person person) {
        return Arrays.asList("5", "6", "15", "16").contains(person.getAttributes().getAttribute(COL_MOBILITY));
    }

    private boolean hasChild(Person person) {
        return false;
    }

    public void completeAttributes(final Person person) {

        String birth = person.getAttributes().getAttribute("dbirth").toString();
        String year = birth.substring(birth.lastIndexOf('-') + 1).replace("\"", "");
        person.getAttributes().putAttribute(COL_AGE, String.valueOf(2016 - Integer.valueOf(year)));
        person.getAttributes().putAttribute(COL_CAR_AVAIL, this.carAvail(person));
        person.getAttributes().putAttribute(COL_GA, this.hasGA(person));
        person.getAttributes().putAttribute(COL_HTA, this.hasHTA(person));
        person.getAttributes().putAttribute(COL_VA, this.hasVA(person));
        person.getAttributes().putAttribute(COL_HAS_CHILD, this.hasChild(person));
    }
}
