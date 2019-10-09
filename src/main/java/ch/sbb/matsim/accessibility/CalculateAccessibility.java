package ch.sbb.matsim.accessibility;

import ch.sbb.matsim.analysis.skims.StreamingFacilities;
import ch.sbb.matsim.zones.Zones;
import ch.sbb.matsim.zones.ZonesLoader;
import ch.sbb.matsim.zones.ZonesQueryCache;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.io.StreamingPopulationReader;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.misc.Counter;
import org.matsim.facilities.ActivityFacility;
import org.matsim.facilities.MatsimFacilitiesReader;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;
import java.util.function.ToDoubleFunction;

public class CalculateAccessibility {

    private final static Logger log = Logger.getLogger(CalculateAccessibility.class);

    private static Map<Coord, Double> calculateAttractions(int gridSize, String facilitiesFilename, String populationFilename, double personWeight) {
        Map<Coord, Double> attractions = new HashMap<>();
        log.info("loading facilities from " + facilitiesFilename);
        ToDoubleFunction<ActivityFacility> weightFunction = f -> {
            double weight = 0;
            String fte = (String) f.getAttributes().getAttribute("fte");
            if (fte != null) {
                weight = 2.54 * Double.parseDouble(fte);
            }
            return weight;
        };
        Counter facCounter = new Counter("#");
        new MatsimFacilitiesReader(null, null, new StreamingFacilities(
                f -> {
                    facCounter.incCounter();
                    double weight = weightFunction.applyAsDouble(f);
                    if (weight > 0) {
                        Coord c = AccessibilityUtils.getGridCoordinate(f.getCoord().getX(), f.getCoord().getY(), gridSize);
                        attractions.compute(c, (k, oldVal) -> oldVal == null ? weight : (oldVal + weight));
                    }
                }
        )).readFile(facilitiesFilename);
        facCounter.printCounter();

        StreamingPopulationReader popReader = new StreamingPopulationReader(ScenarioUtils.createScenario(ConfigUtils.createConfig()));
        popReader.addAlgorithm(p -> {
            Plan plan = p.getSelectedPlan();
            Activity homeAct = (Activity) plan.getPlanElements().get(0);
            if (homeAct.getType().startsWith("home")) {
                Coord c = AccessibilityUtils.getGridCoordinate(homeAct.getCoord().getX(), homeAct.getCoord().getY(), gridSize);
                attractions.compute(c, (k, oldVal) -> oldVal == null ? personWeight : (oldVal + personWeight));
            }
        });
        popReader.readFile(populationFilename);
        return attractions;
    }

    public static void main(String[] args) {
        System.setProperty("matsim.preferLocalDtds", "true");

        String populationFilename = "C:\\devsbb\\codes\\_data\\skims2.1\\MOBi21.10pct.output_plans.xml.gz";
        String networkFilename = "C:\\devsbb\\codes\\_data\\skims2.1\\network.xml.gz";
        String eventsFilename = "C:\\devsbb\\codes\\_data\\skims2.1\\CH.25pct.2.2016.output_events.xml.gz";
//        eventsFilename = null;
        String transitNetworkFilename = "C:\\devsbb\\codes\\_data\\skims2.1\\transitNetwork.xml.gz";
        String scheduleFilename = "C:\\devsbb\\codes\\_data\\skims2.1\\transitSchedule.xml.gz";
        String facilitiesFilename = "C:\\devsbb\\codes\\_data\\skims2.1\\facilities.xml.gz";
        double[] carAMDepTimes = new double[] {6*3600 + 1800, 7*3600 - 600, 7*3600 + 600, 7*3600 + 1800};
        double[] carPMDepTimes = new double[] {17*3600 - 600, 17*3600 + 600, 17*3600 + 1800, 18*3600 - 600};
        String csvOutputFilename = "C:\\devsbb\\codes\\_data\\skims2.1\\accessibility_java1.csv";
        String zonesFilename = "C:\\devsbb\\codes\\_data\\skims2.1\\mobi_zones.shp";
        double ptMinDepTime = 7*3600;
        double ptMaxDepTime = 8*3600;
        double personWeight = 10.0;

        List<Coord> coordinates = new ArrayList<>();

        int gridSize = 1000;

        for (double x = 590_000; x <= 600_000; x += gridSize) {
            for (double y = 190_000; y <= 200_000; y += gridSize) {
                coordinates.add(new Coord(x, y));
            }
        }

        Accessibility.Modes[] modes = new Accessibility.Modes[] {
                new Accessibility.Modes("mm", true, true, true, true),
                new Accessibility.Modes("car", true, false, true, true),
                new Accessibility.Modes("pt", false, true, true, true)
        };

        Map<Coord, Double> attractions = calculateAttractions(gridSize, facilitiesFilename, populationFilename, personWeight);

        BiPredicate<TransitLine, TransitRoute> trainDetector = (tl, tr) -> {
            Object attrVal = tr.getAttributes().getAttribute("01_Datenherkunft");
            String value = attrVal == null ? "" : attrVal.toString();
            return value.contains("Simba");
        };

        Zones zones = new ZonesQueryCache(ZonesLoader.loadZones("mobi", zonesFilename, "ID"));

        Accessibility accessibility = new Accessibility(networkFilename, eventsFilename, scheduleFilename, transitNetworkFilename, attractions, carAMDepTimes, carPMDepTimes, ptMinDepTime, ptMaxDepTime, trainDetector, zones);
        accessibility.setThreadCount(3);
        accessibility.calculateAccessibility(coordinates, modes, csvOutputFilename);
    }
}
