package ch.sbb.matsim.projects.esaf2025;

import ch.sbb.matsim.analysis.LinkVolumeToCSV;
import ch.sbb.matsim.config.variables.SBBActivities;
import ch.sbb.matsim.config.variables.SBBModes;
import ch.sbb.matsim.config.variables.Variables;
import ch.sbb.matsim.csv.CSVReader;
import ch.sbb.matsim.zones.Zone;
import ch.sbb.matsim.zones.Zones;
import ch.sbb.matsim.zones.ZonesLoader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.PopulationFactory;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.io.PopulationWriter;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.geometry.CoordUtils;
import org.matsim.core.utils.geometry.CoordinateTransformation;
import org.matsim.core.utils.geometry.transformations.TransformationFactory;
import org.matsim.core.utils.misc.Time;
import org.matsim.facilities.ActivityFacility;
import java.util.Random;

public final class GenerateESAFPopulation {

    private GenerateESAFPopulation() {
    }

    private final static Logger log = Logger.getLogger(GenerateESAFPopulation.class);

    public static void main(String[] args) throws IOException {

        String path = args[0];
        String part = args[1];
        String csv = path + "\\persons_anzahl_" + part + ".csv";
        String outputPlans = path + "\\plans_" + part + ".xml";
        String zonesFile = args[2];
        int arrivalFrom = Integer.parseInt(args[3]);
        int arrivalTo = Integer.parseInt(args[4]);
        int departureFrom = Integer.parseInt(args[5]);
        int departureTo = Integer.parseInt(args[6]);
        int minDuration = Integer.parseInt(args[7]);
        Zones zones = ZonesLoader.loadZones("zones", zonesFile, Variables.ZONE_ID);
        CoordinateTransformation transformation = TransformationFactory.getCoordinateTransformation(TransformationFactory.WGS84, TransformationFactory.CH1903_LV03_Plus);
        CSVReader csvReader = new CSVReader(csv, ";");
        Id<ActivityFacility> workFacilityId = Id.create("894334", ActivityFacility.class);
        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        Coord workcoord = new Coord(2723602.000000, 1215454.000000);
        var entry = csvReader.readLine();
        var fac = scenario.getPopulation().getFactory();
        Random random = new Random();
        while (entry != null) {
            Id<Person> personId = Id.createPersonId("r_" + part + "_" + entry.get("PERSONNO"));
            int carAvailable = Integer.parseInt(entry.get("caravail"));
            double homex = Double.parseDouble(entry.get("XCOORD"));
            double homey = Double.parseDouble(entry.get("YCOORD"));
            double travelTime = Double.parseDouble(entry.get("travel_time"));
            Coord homeCoord = new Coord(homex, homey);
            double workStart = -1;
            double workEnd = -1;
            if (minDuration == 0) {
                workStart = random.nextDouble() * (arrivalTo - arrivalFrom) + arrivalFrom;
                log.info (workStart);
                workEnd = random.nextDouble() * (departureTo - departureFrom) + departureFrom;
            } else {
                workStart = random.nextDouble() * (arrivalTo - arrivalFrom) + arrivalFrom;
                double workDuration = random.nextDouble() * (departureTo - workStart - minDuration) + minDuration;
                workEnd = workStart + workDuration;
            }
            String mode = SBBModes.CAR;
            if (workEnd - workStart < 0) {
                throw new RuntimeException(personId + " has work end before start");
            }

            Zone homezone = zones.findZone(homeCoord);
            Integer residenceZoneId = Integer.parseInt(entry.get("ZONEID"));
            Integer residenceMsrId = Integer.parseInt(entry.get("MSRID"));
            List<Person> persons = new ArrayList<>();
            Person person = fac.createPerson(personId);
            scenario.getPopulation().addPerson(person);
            persons.add(person);

            createPlan(workFacilityId, workcoord, fac, mode, homeCoord, workStart, workEnd, travelTime, person);

            boolean b2pt = random.nextBoolean();
            Integer dummy = b2pt ? 1 : 0;
            for (var p : persons) {
                p.getAttributes().putAttribute(Variables.CAR_AVAIL, carAvailable);
                p.getAttributes().putAttribute("subpopulation", Variables.REGULAR);
                p.getAttributes().putAttribute("residence_zone_id", residenceZoneId);
                p.getAttributes().putAttribute("residence_msr_id", residenceMsrId);
                p.getAttributes().putAttribute("mode", mode);
                p.getAttributes().putAttribute("level_of_employment_cat", "80_to_100");
                p.getAttributes().putAttribute("pt_subscr", "VA");
                p.getAttributes().putAttribute("age_cat", "45_to_64");
                p.getAttributes().putAttribute("current_edu", "null");
                p.getAttributes().putAttribute("bike2pt", dummy);
                p.getAttributes().putAttribute("bike2pt_act", "home");
                p.getAttributes().putAttribute("ride2pt", dummy);
                p.getAttributes().putAttribute("ride2pt_act", "home");
                p.getAttributes().putAttribute("car2pt_act", "home");
                p.getAttributes().putAttribute("car2pt", dummy);
            }

            entry = csvReader.readLine();
        }

        new PopulationWriter(scenario.getPopulation()).write(outputPlans);

    }

    public static void createPlan(Id<ActivityFacility> workFacilityId, Coord workcoord, PopulationFactory fac, String mode, Coord homeCoord, double work_start,
            double work_end, double travelTime, Person person) {
        if (mode.equals(SBBModes.WALK_FOR_ANALYSIS)) {
            mode = SBBModes.WALK_MAIN_MAINMODE;
        }
        Leg workleg = fac.createLeg(mode);
        Leg homeleg = fac.createLeg(mode);
        Activity home1 = fac.createActivityFromCoord(SBBActivities.home, homeCoord);

        home1.setEndTime(Math.max(0, work_start - travelTime));
        Activity work = fac.createActivityFromActivityFacilityId(SBBActivities.work, workFacilityId);
        work.setCoord(workcoord);
        work.setEndTime(work_end);
        work.setStartTime(work_start);
        Activity home2 = fac.createActivityFromCoord(SBBActivities.home, homeCoord);
        home2.setStartTime(Math.min(work_end + travelTime, 24 * 3600));
        Plan plan = fac.createPlan();
        person.addPlan(plan);
        plan.addActivity(home1);
        plan.addLeg(workleg);
        plan.addActivity(work);
        plan.addLeg(homeleg);
        plan.addActivity(home2);
    }
}
