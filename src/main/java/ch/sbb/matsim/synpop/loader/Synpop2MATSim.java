package ch.sbb.matsim.synpop.loader;

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Population;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.geometry.transformations.CH1903LV03PlustoCH1903LV03;
import org.matsim.facilities.ActivityFacilities;
import org.matsim.facilities.ActivityFacility;
import org.matsim.facilities.ActivityOption;

import java.util.Map;

public class Synpop2MATSim {
    private final static String PERSON_ID = "person_id";
    private final static String HOUSEHOLD_ID = "household_id";
    private final static String BUSINESS_ID = "business_id";

    private final Population population;
    private final Scenario scenario;
    private final ActivityFacilities facilites;

    public Synpop2MATSim() {
        this.scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        this.population = this.scenario.getPopulation();
        this.facilites = this.scenario.getActivityFacilities();
    }

    public Population getPopulation() {
        return this.population;
    }

    public ActivityFacilities getFacilites() {
        return this.facilites;
    }

    public void loadPerson(Map<String, String> map) {
        final Person person = population.getFactory().createPerson(Id.createPersonId(map.get(PERSON_ID)));
        for (String column : map.keySet()) {

            String value = map.get(column);
            if(value.isEmpty()) value = "-1";

            if (column.equals(HOUSEHOLD_ID)) {
                person.getAttributes().putAttribute(column, this.transformHouseholdId(value));
            } else if (column.equals(BUSINESS_ID)) {
                person.getAttributes().putAttribute(column, this.transformBusinessId(value));
            } else if (!column.equals(PERSON_ID)) {
                person.getAttributes().putAttribute(column, value);
            }
        }
        population.addPerson(person);
    }


    private Coord transformCoord(Coord coord) {
        return new CH1903LV03PlustoCH1903LV03().transform(coord);
    }

    private String transformHouseholdId(String id) {
        if(id.equals("-1")) return id;
        return "H_" + id;
    }

    private String transformBusinessId(String id) {
        if(id.equals("-1")) return id;
        return "B_" + id;
    }

    public void loadHousehold(Map<String, String> map) {
        Coord coord = new Coord(Double.valueOf(map.get("X")), Double.valueOf(map.get("Y")));
        Id id = Id.create(this.transformHouseholdId(map.get(HOUSEHOLD_ID)), ActivityFacility.class);
        ActivityFacility facility = facilites.getFactory().createActivityFacility(id, this.transformCoord(coord));
        for (String column : map.keySet()) {
            if (!column.equals(HOUSEHOLD_ID)) {
                facility.getAttributes().putAttribute(column, map.get(column));
            }
        }

        ActivityOption option = facilites.getFactory().createActivityOption("home");
        facility.addActivityOption(option);
        facilites.addActivityFacility(facility);
    }


    public void loadBusiness(Map<String, String> map) {
        Coord coord = new Coord(Double.valueOf(map.get("X")), Double.valueOf(map.get("Y")));
        Id id = Id.create(this.transformBusinessId(map.get(BUSINESS_ID)), ActivityFacility.class);
        ActivityFacility facility = facilites.getFactory().createActivityFacility(id, this.transformCoord(coord));
        for (String column : map.keySet()) {
            if (!column.equals(BUSINESS_ID)) {
                facility.getAttributes().putAttribute(column, map.get(column));
            }
        }

        ActivityOption option = facilites.getFactory().createActivityOption("work");
        facility.addActivityOption(option);
        facilites.addActivityFacility(facility);
    }

}
