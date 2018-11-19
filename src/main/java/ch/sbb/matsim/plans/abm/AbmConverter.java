package ch.sbb.matsim.plans.abm;

import ch.sbb.matsim.config.variables.Activities;
import ch.sbb.matsim.config.variables.Variables;
import ch.sbb.matsim.config.variables.Filenames;
import ch.sbb.matsim.csv.CSVReader;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.*;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.PersonUtils;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.population.io.PopulationReader;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.facilities.ActivityFacility;
import org.matsim.utils.objectattributes.ObjectAttributes;
import org.matsim.utils.objectattributes.ObjectAttributesXmlWriter;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class AbmConverter {

    private static final Logger log = Logger.getLogger(AbmConverter.class);
    private final Map<Id<Person>, List<AbmTrip>> planTable;

    public AbmConverter() {
        this.planTable = new HashMap<>();
    }

    private List<AbmTrip> addPlanIfNotExists(final Id<Person> pid) {
        this.planTable.putIfAbsent(pid, new ArrayList<>());
        return this.planTable.get(pid);
    }


    public Population create_population() {
        final Population population = PopulationUtils.createPopulation(ConfigUtils.createConfig());
        for (final Map.Entry<Id<Person>, List<AbmTrip>> entry : this.planTable.entrySet()) {
            final Id<Person> id = entry.getKey();
            final Person person = PopulationUtils.getFactory().createPerson(id);
            final Plan plan = PopulationUtils.createPlan(person);

            final List<AbmTrip> trips = entry.getValue();
            trips.sort(Comparator.comparing(AbmTrip::getDepTime));

            AbmTrip previousTrip = null;

            for (final AbmTrip trip : trips) {
                final Activity activity = PopulationUtils.createAndAddActivity(plan, Activities.abmActs2matsimActs.get(trip.getoAct()));

                if (previousTrip != null) {
                    activity.setStartTime(previousTrip.getArrtime());
                }
                activity.setEndTime(trip.getDepTime());
                activity.setFacilityId(trip.getOrigFacilityId());
                //activity.setLinkId();
                //activity.setMaximumDuration();
                activity.setCoord(trip.getCoordOrig());

                final Leg leg = PopulationUtils.createLeg(trip.getMode());
                plan.addLeg(leg);

                previousTrip = trip;
            }

            if (previousTrip != null) {
                final Activity activity = PopulationUtils.createAndAddActivity(plan, Activities.abmActs2matsimActs.get(previousTrip.getDestAct()));
                activity.setStartTime(previousTrip.getArrtime());
                activity.setFacilityId(previousTrip.getDestFacilityId());
                //activity.setLinkId();
                //activity.setMaximumDuration();
                activity.setCoord(previousTrip.getCoordDest());

            }
            person.addPlan(plan);
            person.setSelectedPlan(plan);
            population.addPerson(person);
        }
        return population;

    }

    public Population addSynpopAttributes(final Population population, final String synpopFilename) {
        final Scenario synpopScenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        new PopulationReader(synpopScenario).readFile(synpopFilename);
        final Population synpopPopulation = synpopScenario.getPopulation();

        final ObjectAttributes attributes = population.getPersonAttributes();

        for (final Person person : population.getPersons().values()) {
            final Id<Person> pId = Id.createPersonId(person.getId().toString().replace("P_", ""));
            final Person synpopPerson = synpopPopulation.getPersons().get(pId);
            if (synpopPerson != null) {
                for (final Map.Entry<String, Object> entry : synpopPerson.getAttributes().getAsMap().entrySet()) {
                    person.getAttributes().putAttribute(entry.getKey(), entry.getValue());
                }

                PersonUtils.setSex(person, person.getAttributes().getAttribute("sex").toString());
                String carAvailValue = "never";
                if(person.getAttributes().getAttribute("car_avail").equals(true)){
                    carAvailValue = "always";
                }
                PersonUtils.setCarAvail(person,carAvailValue);
                PersonUtils.setLicence(person, person.getAttributes().getAttribute("car_avail").toString());

                PersonUtils.setAge(person, Integer.parseInt(person.getAttributes().getAttribute("age").toString()));
                PersonUtils.setEmployed(person, Integer.parseInt(person.getAttributes().getAttribute("level_of_employment").toString()) > 0);


                person.getAttributes().putAttribute(Variables.SEASON_TICKET, "none");
                if ((boolean) person.getAttributes().getAttribute(Variables.GA)) {
                    person.getAttributes().putAttribute(Variables.SEASON_TICKET, Variables.GA);
                } else if ((boolean) person.getAttributes().getAttribute(Variables.HTA)) {
                    person.getAttributes().putAttribute(Variables.SEASON_TICKET, Variables.HTA);
                }


                person.getAttributes().putAttribute(Variables.SUBPOPULATION, Variables.REGULAR);
                attributes.putAttribute(person.getId().toString(), Variables.SUBPOPULATION, Variables.REGULAR);
            } else {
                log.info("Could not find attributes for person " + person);
            }
        }

        return population;
    }

    public void read(final String pathAbmOutput, final String splitBy) {
        try (final CSVReader reader = new CSVReader(pathAbmOutput, splitBy)) {
            Map<String, String> map;
            while ((map = reader.readLine()) != null) {
                final int id = (int) Double.parseDouble(map.get("pid"));
                final Id<Person> pid = Id.createPersonId("P_" + id);

                final Id<ActivityFacility> origFacilityId = Id.create(map.get("orig_facility_id"), ActivityFacility.class);
                final Id<ActivityFacility> destFacilityId = Id.create(map.get("dest_facility_id"), ActivityFacility.class);

                final String oAct = map.get("orig_act");
                final String dAct = map.get("dest_act");
                final String mode = map.get("mode");

                final int xorig = (int) Double.parseDouble(map.get("Xorig_"));
                final int yorig = (int) Double.parseDouble(map.get("Yorig_"));

                final int xdest = (int) Double.parseDouble(map.get("Xdest_"));
                final int ydest = (int) Double.parseDouble(map.get("Ydest_"));

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


    public void writeOutputs(final String folder, final Population population) {
        final File outputPath = new File(folder);
        if (!outputPath.exists()) {
            outputPath.mkdirs();
        }

        new PopulationWriter(population).write(new File(folder, Filenames.PLANS).toString());
        new ObjectAttributesXmlWriter(population.getPersonAttributes()).writeFile(new File(folder, Filenames.PERSON_ATTRIBUTES).toString());

    }
}
