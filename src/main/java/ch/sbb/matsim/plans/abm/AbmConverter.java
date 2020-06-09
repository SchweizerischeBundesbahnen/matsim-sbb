package ch.sbb.matsim.plans.abm;

import ch.sbb.matsim.config.variables.Filenames;
import ch.sbb.matsim.config.variables.SBBActivities;
import ch.sbb.matsim.config.variables.SBBModes;
import ch.sbb.matsim.config.variables.Variables;
import ch.sbb.matsim.csv.CSVReader;
import ch.sbb.matsim.utils.SBBPersonUtils;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.Population;
import org.matsim.api.core.v01.population.PopulationWriter;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.PersonUtils;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.population.io.PopulationReader;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.core.router.TripStructureUtils.StageActivityHandling;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.misc.OptionalTime;
import org.matsim.facilities.ActivityFacilities;
import org.matsim.facilities.ActivityFacility;

public class AbmConverter {

    private static final Logger log = Logger.getLogger(AbmConverter.class);
    private final Map<Id<Person>, List<AbmTrip>> planTable;
    private final Map<Id<Person>, AbmPersonAttributes> personTable;

    public AbmConverter() {
        this.planTable = new HashMap<>();
        this.personTable = new HashMap<>();
    }

    public void read(final String tripsFileABM, final String personsFileABM) {

        try (final CSVReader reader = new CSVReader(personsFileABM, ";")) {
            Map<String, String> map;
            while ((map = reader.readLine()) != null) {
                final int id = (int) Double.parseDouble(map.get("pid"));
                final Id<Person> pid = Id.createPersonId("P_" + id);

                final int ageCat = (int) Double.parseDouble(map.get("age_cat"));
                final int emplPctCat = (int) Double.parseDouble(map.get("empl_pct_cat"));
                final int eduType = (int) Double.parseDouble(map.get("edu_type"));
                final int mobility = (int) Double.parseDouble(map.get("mobility"));

                final AbmPersonAttributes attributes = new AbmPersonAttributes(ageCat, emplPctCat, eduType, mobility);
                this.personTable.put(pid, attributes);
            }
        } catch (IOException e) {
            log.warn(e);
        }


        try (final CSVReader reader = new CSVReader(tripsFileABM, ";")) {
            Map<String, String> map;
            while ((map = reader.readLine()) != null) {
                final int id = (int) Double.parseDouble(map.get("pid"));
                final Id<Person> pid = Id.createPersonId("P_" + id);

                final Id<ActivityFacility> origFacilityId = Id.create(map.get("orig_facility_id"), ActivityFacility.class);
                final Id<ActivityFacility> destFacilityId = Id.create(map.get("dest_facility_id"), ActivityFacility.class);

                final String oAct = map.get("orig_act");
                final String dAct = map.get("dest_act");
                final String mode = map.get("mode");

                final int xorig = (int) Double.parseDouble(map.get("orig_X"));
                final int yorig = (int) Double.parseDouble(map.get("orig_Y"));

                final int xdest = (int) Double.parseDouble(map.get("dest_X"));
                final int ydest = (int) Double.parseDouble(map.get("dest_Y"));

                final int deptime = (int) (Double.parseDouble(map.get("dep_time")) * 3600);
                final int arrtime = (int) (Double.parseDouble(map.get("arr_time")) * 3600);

                final List<AbmTrip> trips = addPlanIfNotExists(pid);
                final AbmTrip trip = new AbmTrip(origFacilityId, destFacilityId, oAct, dAct, mode, deptime, arrtime, new Coord(xorig, yorig), new Coord(xdest, ydest));
                trips.add(trip);
            }
        } catch (IOException e) {
            log.warn(e);
        }
    }

    private List<AbmTrip> addPlanIfNotExists(final Id<Person> pid) {
        this.planTable.putIfAbsent(pid, new ArrayList<>());
        return this.planTable.get(pid);
    }

    public Population create_population() {
        Population population = PopulationUtils.createPopulation(ConfigUtils.createConfig());

        for (final Map.Entry<Id<Person>, List<AbmTrip>> entry : this.planTable.entrySet()) {
            final Id<Person> id = entry.getKey();
            final Person person = PopulationUtils.getFactory().createPerson(id);

            person.getAttributes().putAttribute("age_cat", this.personTable.get(id).getAgeCat());
            person.getAttributes().putAttribute("empl_pct_cat", this.personTable.get(id).getEmplPctCat());
            person.getAttributes().putAttribute("edu_type", this.personTable.get(id).getEduType());
            person.getAttributes().putAttribute("mobility", this.personTable.get(id).getMobility());

            final Plan plan = PopulationUtils.createPlan(person);

            final List<AbmTrip> trips = entry.getValue();
            trips.sort(Comparator.comparing(AbmTrip::getDepTime));

            AbmTrip previousTrip = null;

            for (final AbmTrip trip : trips) {
                final Activity activity = PopulationUtils.createAndAddActivity(plan, SBBActivities.abmActs2matsimActs.get(trip.getoAct()));

                if (previousTrip != null) {
                    activity.setStartTime(previousTrip.getArrtime());
                }
                activity.setEndTime(trip.getDepTime());
                activity.setFacilityId(trip.getOrigFacilityId());
                activity.setCoord(trip.getCoordOrig());

                final Leg leg = PopulationUtils.createLeg(trip.getMode());
                plan.addLeg(leg);

                previousTrip = trip;
            }

            if (previousTrip != null) {
                final Activity activity = PopulationUtils.createAndAddActivity(plan, SBBActivities.abmActs2matsimActs.get(previousTrip.getDestAct()));
                activity.setStartTime(previousTrip.getArrtime());
                activity.setFacilityId(previousTrip.getDestFacilityId());
                activity.setCoord(previousTrip.getCoordDest());

            }
            person.addPlan(plan);
            person.setSelectedPlan(plan);
            population.addPerson(person);
        }

        return population;
    }

    public void addSynpopAttributes(Population population, final String synpopFilename) {
        final Scenario synpopScenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        new PopulationReader(synpopScenario).readFile(synpopFilename);
        final Population synpopPopulation = synpopScenario.getPopulation();

        for (final Person person : population.getPersons().values()) {
            final Id<Person> pId = Id.createPersonId(person.getId().toString().replace("P_", ""));
            final Person synpopPerson = synpopPopulation.getPersons().get(pId);
            if (synpopPerson != null) {
                /*
                for (final Map.Entry<String, Object> entry : synpopPerson.getAttributes().getAsMap().entrySet()) {
                    person.getAttributes().putAttribute(entry.getKey(), entry.getValue());
                }
                */

                PersonUtils.setSex(person, synpopPerson.getAttributes().getAttribute("sex").toString());

                String carAvailValue = "never";
                if(synpopPerson.getAttributes().getAttribute("car_avail").equals(true)){
                    carAvailValue = "always";
                }
                PersonUtils.setCarAvail(person,carAvailValue);

                PersonUtils.setLicence(person, synpopPerson.getAttributes().getAttribute("car_avail").toString());

                PersonUtils.setAge(person, Integer.parseInt(synpopPerson.getAttributes().getAttribute("age").toString()));
                PersonUtils.setEmployed(person, Integer.parseInt(synpopPerson.getAttributes().getAttribute("level_of_employment").toString()) > 0);

                person.getAttributes().putAttribute(Variables.PT_SUBSCRIPTION, "none");
                if ((boolean) synpopPerson.getAttributes().getAttribute(Variables.GA)) {
                    person.getAttributes().putAttribute(Variables.PT_SUBSCRIPTION, Variables.GA);
                } else if ((boolean) synpopPerson.getAttributes().getAttribute(Variables.VA)) {
                    person.getAttributes().putAttribute(Variables.PT_SUBSCRIPTION, Variables.VA);
                } else if ((boolean) synpopPerson.getAttributes().getAttribute(Variables.HTA)) {
                    person.getAttributes().putAttribute(Variables.PT_SUBSCRIPTION, Variables.HTA);
                }

                person.getAttributes().putAttribute(Variables.SUBPOPULATION, Variables.REGULAR);
            } else {
                log.info("Could not find attributes for person " + person);
            }
        }
    }

    public void adjustModeIfNoLicense(Population population) {
        for (final Person person : population.getPersons().values()) {
            for (Leg leg: TripStructureUtils.getLegs(person.getSelectedPlan()))  {
                if (!PersonUtils.hasLicense(person) && leg.getMode().equals(SBBModes.CAR)) {
                    leg.setMode(TransportMode.ride);
                }
            }
        }
    }

    public void addHomeFacilityAttributes(Population population, ActivityFacilities facilities, String facilityAttribute) {
        for (final Person person : population.getPersons().values()) {
            ActivityFacility facility = SBBPersonUtils.getHomeFacility(person, facilities);
            person.getAttributes().putAttribute(facilityAttribute, facility.getAttributes().getAttribute(facilityAttribute));
        }
    }

    public void createInitialEndTimeAttribute(Population population) {
        for(Person p: population.getPersons().values()) {
            if (p.getAttributes().getAttribute(Variables.INIT_END_TIMES) != null) {
                continue;
            }

            if (p.getPlans().size() > 1) {
                log.info("Person " + p.getId().toString() + " has more than one plan. Taking selected plan...");
            }

            Plan plan = p.getSelectedPlan();
            List<Activity> activities = TripStructureUtils.getActivities(plan, StageActivityHandling.ExcludeStageActivities);
            List<OptionalTime> endTimeList = new ArrayList<>();
            int i = 0;

            for (Activity act : activities) {
                if (i == activities.size() - 1) {
                    break;
                }
                endTimeList.add(act.getEndTime());
                i += 1;
            }

            p.getAttributes()
                    .putAttribute(Variables.INIT_END_TIMES, endTimeList.stream().map(e -> e.isDefined() ? Double.toString(e.seconds()) : Variables.NO_INIT_END_TIME).collect(Collectors.joining("_")));
        }
    }

    public void writeOutputs(Population population, final String folder) {
        final File outputPath = new File(folder);
        if (!outputPath.exists()) {
            outputPath.mkdirs();
        }

        new PopulationWriter(population).write(new File(folder, Filenames.PLANS).toString());
    }
}
