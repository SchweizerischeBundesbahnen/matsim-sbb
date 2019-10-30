package ch.sbb.matsim.accessibility;

import ch.sbb.matsim.analysis.skims.StreamingFacilities;
import ch.sbb.matsim.csv.CSVReader;
import ch.sbb.matsim.csv.CSVWriter;
import ch.sbb.matsim.zones.Zones;
import ch.sbb.matsim.zones.ZonesLoader;
import ch.sbb.matsim.zones.ZonesQueryCache;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.gbl.Gbl;
import org.matsim.core.population.io.StreamingPopulationReader;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.misc.Counter;
import org.matsim.facilities.ActivityFacility;
import org.matsim.facilities.MatsimFacilitiesReader;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
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

    public static void writeAttractions(File file, Map<Coord, Double> attractions) throws IOException {
        try (CSVWriter csv = new CSVWriter(null, new String[] {"x", "y", "attraction"}, file.getAbsolutePath(), StandardCharsets.UTF_8)) {
            attractions.forEach((coord, value) -> {
                csv.set("x", Double.toString(coord.getX()));
                csv.set("y", Double.toString(coord.getY()));
                csv.set("attraction", Double.toString(value));
                csv.writeRow();
            });
        }
    }

    public static Map<Coord, Double> loadAttractions(File file) throws IOException {
        Map<Coord, Double> attractions = new HashMap<>();
        try (CSVReader csv = new CSVReader(new String[] {"x", "y", "attraction"}, file.getAbsolutePath(), ";")) {
            Map<String, String> data = csv.readLine(); // header
            while ((data = csv.readLine()) != null) {
                attractions.put(new Coord(Double.parseDouble(data.get("x")), Double.parseDouble(data.get("y"))), Double.parseDouble(data.get("attraction")));
            }
        }
        return attractions;
    }

    public static void main(String[] args) throws IOException {
        System.setProperty("matsim.preferLocalDtds", "true");

        Thread memObs = new Thread(() -> {
            while (true) {
                Gbl.printMemoryUsage();
                try {
                    Thread.sleep(30_000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }, "Memory Observer");
        memObs.setDaemon(true);
        memObs.start();

        String populationFilename = "C:\\devsbb\\codes\\_data\\skims2.1\\MOBi21.10pct.output_plans.xml.gz";
        String networkFilename = "C:\\devsbb\\codes\\_data\\skims2.1\\network.xml.gz";
        String eventsFilename = "C:\\devsbb\\codes\\_data\\skims2.1\\CH.25pct.2.2016.output_events.xml.gz";
        eventsFilename = null;
        String transitNetworkFilename = "C:\\devsbb\\codes\\_data\\skims2.1\\transitNetwork.xml.gz";
        String scheduleFilename = "C:\\devsbb\\codes\\_data\\skims2.1\\transitSchedule.xml.gz";
        String facilitiesFilename = "C:\\devsbb\\codes\\_data\\skims2.1\\facilities.xml.gz";
        double[] carAMDepTimes = new double[] {6*3600 + 1800, 7*3600 - 600, 7*3600 + 600, 7*3600 + 1800};
        double[] carPMDepTimes = new double[] {17*3600 - 600, 17*3600 + 600, 17*3600 + 1800, 18*3600 - 600};
        String csvOutputFilename = "C:\\devsbb\\codes\\_data\\skims2.1\\accessibility_java_debug.csv";
        String zonesFilename = "C:\\devsbb\\codes\\_data\\skims2.1\\mobi_zones.shp";
        int gridSize = 300;
        String attractionsFilename = "C:\\devsbb\\codes\\_data\\skims2.1\\attractions_" + gridSize + ".csv";
        double ptMinDepTime = 7*3600;
        double ptMaxDepTime = 8*3600;
        double personWeight = 10.0;
        int numThreads = 1;

        List<Coord> coordinates = new ArrayList<>();


        for (double x = 2_580_000; x <= 2_603_000; x += gridSize) {
            for (double y = 1_180_000; y <= 1_203_000; y += gridSize) {
                coordinates.add(new Coord(x, y));
            }
        }

        Accessibility.Modes[] modes = new Accessibility.Modes[] {
                new Accessibility.Modes("mm", true, true, true, true),
                new Accessibility.Modes("car", true, false, true, true),
                new Accessibility.Modes("pt", false, true, true, true)
        };

        Map<Coord, Double> attractions = null;
        File attractionsFile = new File(attractionsFilename);
        if (attractionsFile.exists()) {
            log.info("loading attractions from " + attractionsFile.getAbsolutePath());
            attractions = loadAttractions(attractionsFile);
        } else {
            log.info("calculate attractions...");
            attractions = calculateAttractions(gridSize, facilitiesFilename, populationFilename, personWeight);
            log.info("write attractions to " + attractionsFile.getAbsolutePath());
            writeAttractions(attractionsFile, attractions);
        }

        BiPredicate<TransitLine, TransitRoute> trainDetector = (tl, tr) -> {
            Object attrVal = tr.getAttributes().getAttribute("01_Datenherkunft");
            String value = attrVal == null ? "" : attrVal.toString();
            return value.contains("Simba");
        };

        Zones zones = new ZonesQueryCache(ZonesLoader.loadZones("mobi", zonesFilename, "ID"));

        log.info("calculate accessibility...");
        Accessibility accessibility = new Accessibility(networkFilename, eventsFilename, scheduleFilename, transitNetworkFilename, attractions, carAMDepTimes, carPMDepTimes, ptMinDepTime, ptMaxDepTime, trainDetector, zones);
        accessibility.setThreadCount(numThreads);
        accessibility.calculateAccessibility(coordinates, modes, csvOutputFilename);

        log.info("done.");
    }
}
