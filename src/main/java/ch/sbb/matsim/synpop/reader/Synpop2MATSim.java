package ch.sbb.matsim.synpop.reader;

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
import org.matsim.households.Households;

import java.util.Map;

public class Synpop2MATSim {
    private Population population;
    private Scenario scenario;
    private Households households;
    private ActivityFacilities facilites;

    public Synpop2MATSim() {
        this.scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        this.population = this.scenario.getPopulation();
        this.households = this.scenario.getHouseholds();
        this.facilites = this.scenario.getActivityFacilities();

    }

    public Population getPopulation() {
        return this.population;
    }
    public ActivityFacilities getFacilites(){
        return this.facilites;
    }

    public void loadPerson(Map<String, String> map) {
        Person person = population.getFactory().createPerson(Id.createPersonId(map.get("person_id")));
        for (String column : map.keySet()) {
            if (column.equals("household_id")) {
                person.getAttributes().putAttribute(column, this.transformHouseholdId(map.get(column)));
            } else if (!column.equals("person_id")) {
                person.getAttributes().putAttribute(column, map.get(column));
            }

        }
        population.addPerson(person);
    }


    private Coord transformCoord(Coord coord) {
        return new CH1903LV03PlustoCH1903LV03().transform(coord);
    }

    private String transformHouseholdId(String id) {
        return "h_" + id;
    }

    public void loadHousehold(Map<String, String> map) {
        Coord coord = new Coord(Double.valueOf(map.get("X")), Double.valueOf(map.get("Y")));
        Id id = Id.create(this.transformHouseholdId(map.get("household_id")), ActivityFacility.class);
        ActivityFacility facility = facilites.getFactory().createActivityFacility(id, this.transformCoord(coord));
        for (String column : map.keySet()) {
            if (!column.equals("household_id")) {
                facility.getAttributes().putAttribute(column, map.get(column));
            }
        }

        ActivityOption option = facilites.getFactory().createActivityOption("home");
        facility.addActivityOption(option);
        facilites.addActivityFacility(facility);
    }


    public void loadBusiness(Map<String, String> map) {
        Coord coord = new Coord(Double.valueOf(map.get("X")), Double.valueOf(map.get("Y")));
        Id id = Id.create(map.get("business_id"), ActivityFacility.class);
        ActivityFacility facility = facilites.getFactory().createActivityFacility(id, this.transformCoord(coord));
        for (String column : map.keySet()) {
            if (!column.equals("business_id")) {
                facility.getAttributes().putAttribute(column, map.get(column));
            }
        }

        ActivityOption option = facilites.getFactory().createActivityOption("work");
        facility.addActivityOption(option);
        facilites.addActivityFacility(facility);
    }

}
