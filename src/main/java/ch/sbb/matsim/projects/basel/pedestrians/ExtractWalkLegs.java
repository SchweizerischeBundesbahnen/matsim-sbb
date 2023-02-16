package ch.sbb.matsim.projects.basel.pedestrians;

import ch.sbb.matsim.config.variables.SBBModes;
import ch.sbb.matsim.config.variables.Variables;
import ch.sbb.matsim.preparation.casestudies.MergeRoutedAndUnroutedPlans;
import ch.sbb.matsim.zones.Zones;
import ch.sbb.matsim.zones.ZonesLoader;
import org.apache.commons.lang3.mutable.MutableInt;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.population.*;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.gbl.MatsimRandom;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.population.io.PopulationReader;
import org.matsim.core.population.io.PopulationWriter;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.geometry.CoordUtils;

import java.util.*;

public class ExtractWalkLegs {

    public static final String STATION_ACT = "station";
    public static final String PT_INTERACTION = "pt interaction";
    public static final String ACTIVITY = "activity";
    public static final String TRANSFER = "transfer";
    //VIA does not like visualizing interaction activities
    public static final double TIMEVARIATION = 300;
    public static final double THRESHOLD_DISTANCE = 1000;
    private final Population population;
    private final List<Id<Link>> stopFacilityIds;
    private final Set<String> relevantZones;
    private final int scaleFactor;
    private final Zones zones;
    private final Set<Coord> focusCoords;
    private final Population outputPopulation;
    private final Random random = MatsimRandom.getRandom();

    public ExtractWalkLegs(Population population, List<Id<Link>> stopFacilityIds, Set<String> relevantZones, int scaleFactor, Zones zones, Set<Coord> focusCoords) {
        this.population = population;
        this.stopFacilityIds = stopFacilityIds;
        this.relevantZones = relevantZones;
        this.scaleFactor = scaleFactor;
        this.zones = zones;
        this.focusCoords = focusCoords;
        this.outputPopulation = PopulationUtils.createPopulation(ConfigUtils.createConfig());

    }

    public static void main(String[] args) {
        String inputPlans = "\\\\wsbbrz0283\\mobi\\40_Projekte\\20220412_Basel_2050\\pedestrians_basel_sbb\\v101\\v101-outputplans-experienced-basel-sbb.xml.gz";
//        String inputPlans = "\\\\wsbbrz0283\\mobi\\40_Projekte\\20220412_Basel_2050\\pedestrians_basel_sbb\\testperson.xml";
        String outputPlans = "\\\\wsbbrz0283\\mobi\\40_Projekte\\20220412_Basel_2050\\pedestrians_basel_sbb\\v101\\v101-basel-sbb-legs.xml.gz";
//        String outputPlans = "\\\\wsbbrz0283\\mobi\\40_Projekte\\20220412_Basel_2050\\pedestrians_basel_sbb\\testconversion.xml";
        String zonesFile = "\\\\wsbbrz0283\\mobi\\40_Projekte\\20220412_Basel_2050\\plans\\v200\\mobi-zones.shp";
        List<Id<Link>> stopFacilityIds = List.of(Id.create("pt_1388", Link.class), Id.create("pt_100001204", Link.class));
        Set<String> relevantZones = Set.of("27010106");
        int scaleFactor = 2;
        Coord basel = new Coord(2611360.86388353, 1266277.81032902);


        Zones zones = ZonesLoader.loadZones("zones", zonesFile, Variables.ZONE_ID);

        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        new PopulationReader(scenario).readFile(inputPlans);
        Population population = scenario.getPopulation();

        ExtractWalkLegs extractWalkLegs = new ExtractWalkLegs(population, stopFacilityIds, relevantZones, scaleFactor, zones, Collections.singleton(basel));
        extractWalkLegs.run();
        extractWalkLegs.analyzeBasicFlows();
        new PopulationWriter(extractWalkLegs.getOutputPopulation()).write(outputPlans);
    }

    public Population getOutputPopulation() {
        return outputPopulation;
    }

    private void run() {
        for (Person p : population.getPersons().values()) {
            Plan plan = p.getSelectedPlan();
            int copy = 0;
            for (TripStructureUtils.Trip trip : TripStructureUtils.getTrips(plan)) {
                Activity previousActivity = trip.getOriginActivity();
                Leg leg = null;
                for (PlanElement planElement : trip.getTripElements()) {
                    if (planElement instanceof Leg) {
                        leg = (Leg) planElement;
                    }
                    if (planElement instanceof Activity) {
                        Activity currentActivity = (Activity) planElement;
                        if (checkLeg(previousActivity, leg, currentActivity)) {
                            copy += addLegToPopulation(p.getId(), previousActivity, leg, currentActivity, copy);
                        }
                        previousActivity = currentActivity;
                    }
                }
                if (checkLeg(previousActivity, leg, trip.getDestinationActivity())) {
                    copy += addLegToPopulation(p.getId(), previousActivity, leg, trip.getDestinationActivity(), copy);
                }
            }
        }

    }

    private int addLegToPopulation(Id<Person> personId, Activity prevAct, Leg currentLeg, Activity nextAct, int clone) {
        for (int i = 0; i < this.scaleFactor; i++) {
            Id<Person> newPersonId = Id.createPersonId(personId.toString() + "_" + (clone + i));
            PopulationFactory factory = outputPopulation.getFactory();
            Person p = factory.createPerson(newPersonId);
            outputPopulation.addPerson(p);
            Plan plan = factory.createPlan();
            p.addPlan(plan);
            double timeVariation = 2 * TIMEVARIATION * (random.nextDouble() - 1.0);

            Activity before = factory.createActivityFromCoord(prevAct.getType(), prevAct.getCoord());
            before.setEndTime(currentLeg.getDepartureTime().seconds() + timeVariation);
            before.setLinkId(prevAct.getLinkId());
            plan.addActivity(before);
            Leg leg = factory.createLeg(SBBModes.ACCESS_EGRESS_WALK);
            leg.setDepartureTime(before.getEndTime().seconds());
            plan.addLeg(leg);
            Activity after = factory.createActivityFromCoord(nextAct.getType(), nextAct.getCoord());
            after.setLinkId(nextAct.getLinkId());
            plan.addActivity(after);
        }

        return this.scaleFactor;
    }

    private boolean checkLeg(Activity prevAct, Leg prevLeg, Activity nextAct) {
        boolean isInPerimeter = false;
        if (prevLeg.getMode().equals(SBBModes.WALK_MAIN_MAINMODE) || prevLeg.getMode().equals(SBBModes.ACCESS_EGRESS_WALK)) {
            if (prevLeg.getRoute().getStartLinkId() != prevAct.getLinkId()) return false;
            if (prevLeg.getRoute().getEndLinkId() != nextAct.getLinkId()) return false;
            if (prevLeg.getRoute().getStartLinkId().equals(prevLeg.getRoute().getEndLinkId())) {
                if (!prevLeg.getRoute().getStartLinkId().toString().startsWith("pt_")) return false;
            }
            if (checkActivity(prevAct) || checkActivity(nextAct)) {
                isInPerimeter = true;
            }
        }
        return isInPerimeter;
    }

    private void analyzeBasicFlows() {
        Set<String> relevantStops = Set.of("pt_1388", "pt_100001204");
        Map<String, MutableInt> relevantRelations = Map.of("pt_1388-pt_1388", new MutableInt(),
                "pt_1388-pt_100001204", new MutableInt(),
                "pt_100001204-pt_1388", new MutableInt(),
                "pt_100001204-pt_100001204", new MutableInt(),
                "pt_100001204-out", new MutableInt(),
                "pt_1388-out", new MutableInt(),
                "out-pt_100001204", new MutableInt(),
                "out-pt_1388", new MutableInt(),
                "out-out", new MutableInt());
        outputPopulation.getPersons().values().stream().map(person -> person.getSelectedPlan()).map(plan ->
        {
            String f = ((Activity) plan.getPlanElements().get(0)).getLinkId().toString();
            if (!relevantStops.contains(f)) f = "out";
            String t = ((Activity) plan.getPlanElements().get(2)).getLinkId().toString();
            if (!relevantStops.contains(t)) t = "out";
            return new StringBuilder().append(f).append("-").append(t).toString();
        }).forEach(s -> relevantRelations.get(s).increment());

        relevantRelations.forEach((s, i) -> System.out.println(s + " \t " + i));

    }

    private boolean checkActivity(Activity activity) {
        Id<Link> linkId = activity.getLinkId();
        if (linkId != null) {
            if (this.stopFacilityIds.contains(linkId)) {
                return true;
            }
        }
        Coord activityCoord = activity.getCoord();
        if (activityCoord == null) {
            return false;
        } else if (MergeRoutedAndUnroutedPlans.isCoordinWhiteListZone(this.relevantZones, this.zones, activityCoord)) {
            return true;
        } else
            return focusCoords.stream().anyMatch(focusCoord -> CoordUtils.calcEuclideanDistance(activityCoord, focusCoord) < THRESHOLD_DISTANCE);
    }


}
