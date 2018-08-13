package ch.sbb.matsim.synpop.attributes;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.population.Person;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class PersonAttributes {
    private final static Logger log = Logger.getLogger(PersonAttributes.class);
    Map<String, String> position_in_hh_map = new HashMap<>();
    Map<String, String> position_in_bus_map = new HashMap<>();
    Map<String, String> position_in_edu_map = new HashMap<>();
    Map<String, String> sex_map = new HashMap<>();
    Map<String, String> education_map = new HashMap<>();
    Map<String, String> language_map = new HashMap<>();
    Map<String, String> sector_map = new HashMap<>();
    Map<String, String> ownership_map = new HashMap<>();
    Map<String, String> mobility_map = new HashMap<>();

    public final static String COL_PERSON_ID = "person_id";
    public final static String COL_HOUSEHOLD_ID = "household_id";
    public final static String COL_BUSINESS_ID = "business_id";
    public final static String COL_SECTOR = "sector";
    public final static String COL_LEVEL_OF_EMPLOYMENT = "level_of_employment";
    public final static String COL_OWNERSHIP = "ownership";
    public final static String COL_POSITION_IN_HH = "position_in_hh";
    public final static String COL_POSITION_IN_BUS = "position_in_bus";
    public final static String COL_POSITION_IN_EDU = "position_in_edu";
    public final static String COL_SEX = "sex";
    public final static String COL_EDUCATION = "education";
    public final static String COL_LANGUAGE = "language";
    public final static String COL_MOBILITY = "mobility";
    public final static String COL_AGE = "age";
    public final static String COL_HAS_CHILD = "has_child";
    public final static String COL_GA = "GA";
    public final static String COL_HTA = "HTA";
    public final static String COL_VA = "VA";
    public final static String COL_NUMBER_CAR = "number_of_cars";
    public final static String COL_CAR_AVAIL = "car_avail";
    public final static String COL_INCOME = "income";

    public PersonAttributes() {
        this.createMaps();
    }

    private void createMaps() {
        position_in_hh_map.put("3", "child");
        position_in_hh_map.put("5", "partner");
        position_in_hh_map.put("0", "other");

        position_in_bus_map.put("0", "unemployed");
        position_in_bus_map.put("1", "CEO");
        position_in_bus_map.put("11", "business_management");
        position_in_bus_map.put("12", "management");
        position_in_bus_map.put("20", "employee");
        position_in_bus_map.put("3", "apprentice");

        position_in_edu_map.put("10", "pupil");
        position_in_edu_map.put("20", "student");

        sex_map.put("1", "male");
        sex_map.put("0", "female");

        education_map.put("1", "without post-compulsory education");
        education_map.put("2", "Secondary School II");
        education_map.put("3", "tertiary education (higher education)");
        education_map.put("4", "tertiary education (university)");

        language_map.put("1", "german");
        language_map.put("2", "french");
        language_map.put("3", "italian");
        language_map.put("4", "rumantsch");
        language_map.put("5", "other");

        sector_map.put("1", "agriculture");
        sector_map.put("2", "production");
        sector_map.put("3", "wholesale");
        sector_map.put("4", "retail");
        sector_map.put("5", "gastronomy");
        sector_map.put("6", "finance");
        sector_map.put("7", "services fC");
        sector_map.put("8", "other services");
        sector_map.put("9", "others");
        sector_map.put("10", "non movers");

        ownership_map.put("0", "no");
        ownership_map.put("1", "yes");

        mobility_map.put("0", "no mobility tool available");
        mobility_map.put("1", "Half-fare travel card (Halbtax)");
        mobility_map.put("2", "Travel card (GA)");
        mobility_map.put("5", "Local travel card");
        mobility_map.put("6", "Local travel card and Swiss half-fare travel card");
        mobility_map.put("10", "Car available");
        mobility_map.put("11", "Car available and half-fare travel card");
        mobility_map.put("12", "Car available and travel card (GA)");
        mobility_map.put("15", "Car available and local travel card");
        mobility_map.put("16", "Car available, local travel card and Swiss half-fare travel card");

    }


    private void mapAttribute(Person person, String column, Map<String, String> map, String defaultValue) {
        String value = map.getOrDefault(person.getAttributes().getAttribute(column).toString(), defaultValue);
        person.getAttributes().putAttribute("N_" + column, value);
    }

    private void changeColumnName(Person person, String oldColumn, String newColumn) {
        Object attribute = person.getAttributes().getAttribute(oldColumn);
        person.getAttributes().putAttribute(newColumn, attribute);
        person.getAttributes().removeAttribute(oldColumn);
    }

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
        this.changeColumnName(person, "type_1", COL_SECTOR);
        this.changeColumnName(person, "type_2", COL_LEVEL_OF_EMPLOYMENT);
        this.changeColumnName(person, "type_4", COL_OWNERSHIP);
        this.changeColumnName(person, "car_ownership", COL_NUMBER_CAR);


        this.mapAttribute(person, COL_POSITION_IN_HH, position_in_hh_map, "");
        this.mapAttribute(person, COL_POSITION_IN_BUS, position_in_bus_map, "");
        this.mapAttribute(person, COL_POSITION_IN_EDU, position_in_edu_map, "");
        this.mapAttribute(person, COL_SEX, sex_map, "");
        this.mapAttribute(person, COL_EDUCATION, education_map, "");
        this.mapAttribute(person, COL_LANGUAGE, language_map, "");
        this.mapAttribute(person, COL_SECTOR, sector_map, "");
        this.mapAttribute(person, COL_OWNERSHIP, ownership_map, "");
        this.mapAttribute(person, COL_MOBILITY, mobility_map, "");


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
