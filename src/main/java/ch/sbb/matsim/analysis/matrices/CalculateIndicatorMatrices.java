/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2018.
 */

package ch.sbb.matsim.analysis.matrices;

import ch.sbb.matsim.analysis.matrices.NetworkTravelTimeMatrix.NetworkIndicators;
import ch.sbb.matsim.csv.CSVWriter;
import ch.sbb.matsim.routing.pt.raptor.RaptorParameters;
import ch.sbb.matsim.routing.pt.raptor.RaptorStaticConfig;
import ch.sbb.matsim.routing.pt.raptor.RaptorUtils;
import ch.sbb.matsim.routing.pt.raptor.SwissRailRaptorData;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.events.EventsUtils;
import org.matsim.core.events.MatsimEventsReader;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.algorithms.TransportModeNetworkFilter;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.core.router.costcalculators.OnlyTimeDependentTravelDisutility;
import org.matsim.core.router.util.TravelDisutility;
import org.matsim.core.router.util.TravelTime;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.trafficmonitoring.FreeSpeedTravelTime;
import org.matsim.core.trafficmonitoring.TravelTimeCalculator;
import org.matsim.core.utils.collections.CollectionUtils;
import org.matsim.core.utils.gis.ShapeFileReader;
import org.matsim.core.utils.misc.Counter;
import org.matsim.core.utils.misc.Time;
import org.matsim.facilities.MatsimFacilitiesReader;
import org.matsim.pt.transitSchedule.api.TransitScheduleReader;
import org.opengis.feature.simple.SimpleFeature;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * @author mrieser / SBB
 */
public class CalculateIndicatorMatrices {

    private static final Logger log = Logger.getLogger(CalculateIndicatorMatrices.class);

    static final String CAR_TRAVELTIMES_FILENAME = "car_traveltimes.csv.gz";
    static final String CAR_DISTANCES_FILENAME = "car_distances.csv.gz";
    static final String PT_TRAVELTIMES_FILENAME = "pt_traveltimes.csv.gz";
    static final String PT_ACCESSTIMES_FILENAME = "pt_accesstimes.csv.gz";
    static final String PT_EGRESSTIMES_FILENAME = "pt_egresstimes.csv.gz";
    static final String PT_FREQUENCIES_FILENAME = "pt_frequencies.csv.gz";
    static final String PT_ADAPTIONTIMES_FILENAME = "pt_adaptiontimes.csv.gz";
    static final String PT_TRAINSHARE_BYDISTANCE_FILENAME = "pt_trainshare_bydistance.csv.gz";
    static final String PT_TRAINSHARE_BYTIME_FILENAME = "pt_trainshare_bytime.csv.gz";
    static final String PT_TRANSFERCOUNTS_FILENAME = "pt_transfercounts.csv.gz";
    static final String BEELINE_DISTANCE_FILENAME = "beeline_distances.csv.gz";
    static final String ZONE_LOCATIONS_FILENAME = "zone_coordinates.csv";

    private final static GeometryFactory GEOMETRY_FACTORY = new GeometryFactory();

    public static void main(String[] args) throws IOException {
        System.setProperty("matsim.preferLocalDtds", "true");

        String zonesShapeFilename = args[0];
        String zonesIdAttributeName = args[1];
        String facilitiesFilename = args[2];
        String networkFilename = args[3];
        String transitScheduleFilename = args[4];
        String eventsFilename = args[5];
        String outputDirectory = args[6];
        int numberOfPointsPerZone = Integer.valueOf(args[7]);
        int numberOfThreads = Integer.valueOf(args[8]);
        String[] timesCarStr = args[9].split(";");
        String[] timesPtStr = args[10].split(";");
        Set<String> modes = CollectionUtils.stringToSet(args[11]);

        double[] timesCar = new double[timesCarStr.length];
        for (int i = 0; i < timesCarStr.length; i++)
            timesCar[i] = Time.parseTime(timesCarStr[i]);

        double[] timesPt = new double[timesPtStr.length];
        for (int i = 0; i < timesPtStr.length; i++)
            timesPt[i] = Time.parseTime(timesPtStr[i]);

        Config config = ConfigUtils.createConfig();
        Scenario scenario = ScenarioUtils.createScenario(config);

        File outputDir = new File(outputDirectory);
        if (!outputDir.exists()) {
            log.info("create output directory " + outputDirectory);
            outputDir.mkdirs();
        } else {
            log.warn("output directory exists already, might overwrite data. " + outputDirectory);
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                e.printStackTrace();
                return;
            }
        }

        // load all data

        log.info("loading network from " + networkFilename);
        new MatsimNetworkReader(scenario.getNetwork()).readFile(networkFilename);
        log.info("loading schedule from " + transitScheduleFilename);
        new TransitScheduleReader(scenario).readFile(transitScheduleFilename);

        log.info("loading zones from " + zonesShapeFilename);
        Collection<SimpleFeature> zones = new ShapeFileReader().readFileAndInitialize(zonesShapeFilename);
        Map<String, SimpleFeature> zonesById = new HashMap<>();
        for (SimpleFeature zone : zones) {
            String zoneId = zone.getAttribute(zonesIdAttributeName).toString();
            zonesById.put(zoneId, zone);
        }

        TravelTime tt = null;
        if (modes.contains(TransportMode.car)) {
            if (eventsFilename != null) {
                log.info("extracting actual travel times from " + eventsFilename);
                TravelTimeCalculator ttc = TravelTimeCalculator.create(scenario.getNetwork(), config.travelTimeCalculator());
                EventsManager events = EventsUtils.createEventsManager();
                events.addHandler(ttc);
                new MatsimEventsReader(events).readFile(eventsFilename);
                tt = ttc.getLinkTravelTimes();
            } else {
                tt = new FreeSpeedTravelTime();
                log.info("No events specified. Travel Times will be calculated with free speed travel times.");
            }
        }

        TravelDisutility td = new OnlyTimeDependentTravelDisutility(tt);

        // load facilities
        log.info("loading facilities from " + facilitiesFilename);

        Counter facCounter = new Counter("#");
        List<WeightedFacility> facilities = new ArrayList<>();
        new MatsimFacilitiesReader(null, null, new StreamingFacilities(
                f -> {
                    facCounter.incCounter();
                    double weight = 2; // default for households
                    String fte = (String) f.getAttributes().getAttribute("fte");
                    if (fte != null) {
                        weight = Double.parseDouble(fte);
                    }
                    WeightedFacility wf = new WeightedFacility(f.getCoord(), weight);
                    facilities.add(wf);
                }
        )).readFile(facilitiesFilename);
        facCounter.printCounter();

        log.info("assign facilities to zones...");
        Map<String, List<WeightedFacility>> facilitiesPerZone = new HashMap<>();
        Counter facCounter2 = new Counter("# ");
        for (WeightedFacility fac : facilities) {
            facCounter2.incCounter();
            String zoneId = findZone(fac.coord, zonesById);
            if (zoneId != null) {
                facilitiesPerZone.computeIfAbsent(zoneId, k -> new ArrayList<>()).add(fac);
            }
        }
        facCounter2.printCounter();

        // define points per zone
        log.info("choose coordinates (facilities) per zone...");

        Map<String, Coord[]> coordsPerZone = new HashMap<>();
        Random r = new Random(20180404L);

        for (Map.Entry<String, List<WeightedFacility>> e : facilitiesPerZone.entrySet()) {
            String zoneId = e.getKey();
            List<WeightedFacility> zoneFacilities = e.getValue();
            double sumWeight = 0.0;
            for (WeightedFacility f : zoneFacilities) {
                sumWeight += f.weight;
            }
            Coord[] coords = new Coord[numberOfPointsPerZone];
            for (int i = 0; i < numberOfPointsPerZone; i++) {
                double weight = r.nextDouble() * sumWeight;
                double sum = 0.0;
                WeightedFacility chosenFac = null;
                for (WeightedFacility f : zoneFacilities) {
                    sum += f.weight;
                    if (weight <= sum) {
                        chosenFac = f;
                        break;
                    }
                }
                coords[i] = chosenFac.coord;
            }
            coordsPerZone.put(zoneId, coords);
        }
        facilities.clear();// free memory

        File coordFile = new File(outputDir, ZONE_LOCATIONS_FILENAME);
        log.info("write chosen coordinates to file " + coordFile.getAbsolutePath());
        try (CSVWriter writer = new CSVWriter(null, new String[] {"ZONE", "PT_INDEX", "X", "Y"}, coordFile.getAbsolutePath())) {
            for (Map.Entry<String, Coord[]> e : coordsPerZone.entrySet()) {
                String zoneId = e.getKey();
                Coord[] coords = e.getValue();
                for (int i = 0; i < coords.length; i++) {
                    Coord coord = coords[i];
                    writer.set("ZONE", zoneId);
                    writer.set("PT_INDEX", Integer.toString(i));
                    writer.set("X", Double.toString(coord.getX()));
                    writer.set("Y", Double.toString(coord.getY()));
                    writer.writeRow();
                }
            }
        }

        // calc MIV matrix

        if (modes.contains(TransportMode.car)) {
            log.info("extracting car-only network");
            final Network carNetwork = NetworkUtils.createNetwork();
            new TransportModeNetworkFilter(scenario.getNetwork()).filter(carNetwork, Collections.singleton(TransportMode.car));

            log.info("calc CAR matrix for " + Time.writeTime(timesCar[0]));
            NetworkIndicators<String> netIndicators = NetworkTravelTimeMatrix.calculateTravelTimeMatrix(carNetwork, zonesById, coordsPerZone, timesCar[0], tt, td, numberOfPointsPerZone, numberOfThreads);

            if (tt instanceof FreeSpeedTravelTime) {
                log.info("Do not calculate CAR matrices for other times as only freespeed is being used");
            } else {
                for (int i = 1; i < timesCar.length; i++) {
                    log.info("calc CAR matrices for " + Time.writeTime(timesCar[i]));
                    NetworkIndicators<String> indicators2 = NetworkTravelTimeMatrix.calculateTravelTimeMatrix(carNetwork, zonesById, coordsPerZone, timesCar[i], tt, td, numberOfPointsPerZone, numberOfThreads);
                    log.info("merge CAR matrices for " + Time.writeTime(timesCar[i]));
                    combineMatrices(netIndicators.travelTimeMatrix, indicators2.travelTimeMatrix);
                    combineMatrices(netIndicators.distanceMatrix, indicators2.distanceMatrix);
                }
                log.info("re-scale CAR matrices after all data is merged.");
                netIndicators.travelTimeMatrix.multiply((float) (1.0 / timesCar.length));
                netIndicators.distanceMatrix.multiply((float) (1.0 / timesCar.length));
            }

            log.info("write CAR matrices to " + outputDirectory);
            FloatMatrixIO.writeAsCSV(netIndicators.travelTimeMatrix, outputDirectory + "/" + CAR_TRAVELTIMES_FILENAME);
            FloatMatrixIO.writeAsCSV(netIndicators.distanceMatrix, outputDirectory + "/" + CAR_DISTANCES_FILENAME);
        }

        // calc PT matrices

        if (modes.contains(TransportMode.pt)) {
            log.info("prepare PT Matrix calculation");
            RaptorStaticConfig raptorConfig = RaptorUtils.createStaticConfig(config);
            raptorConfig.setOptimization(RaptorStaticConfig.RaptorOptimization.OneToAllRouting);
            SwissRailRaptorData raptorData = SwissRailRaptorData.create(scenario.getTransitSchedule(), raptorConfig, scenario.getNetwork());
            RaptorParameters raptorParameters = RaptorUtils.createParameters(config);

            log.info("calc PT matrices for " + Time.writeTime(timesPt[0]) + " - " + Time.writeTime(timesPt[1]));
            PTFrequencyMatrix.PtIndicators<String> matrices = PTFrequencyMatrix.calculateTravelTimeMatrix(raptorData, zonesById, coordsPerZone, timesPt[0], timesPt[1], 120, raptorParameters, numberOfThreads);

            log.info("write PT matrices to " + outputDirectory);
            FloatMatrixIO.writeAsCSV(matrices.adaptionTimeMatrix, outputDirectory + "/" + PT_ADAPTIONTIMES_FILENAME);
            FloatMatrixIO.writeAsCSV(matrices.frequencyMatrix, outputDirectory + "/" + PT_FREQUENCIES_FILENAME);
            FloatMatrixIO.writeAsCSV(matrices.travelTimeMatrix, outputDirectory + "/" + PT_TRAVELTIMES_FILENAME);
            FloatMatrixIO.writeAsCSV(matrices.accessTimeMatrix, outputDirectory + "/" + PT_ACCESSTIMES_FILENAME);
            FloatMatrixIO.writeAsCSV(matrices.egressTimeMatrix, outputDirectory + "/" + PT_EGRESSTIMES_FILENAME);
            FloatMatrixIO.writeAsCSV(matrices.transferCountMatrix, outputDirectory + "/" + PT_TRANSFERCOUNTS_FILENAME);
            FloatMatrixIO.writeAsCSV(matrices.trainTravelTimeShareMatrix, outputDirectory + "/" + PT_TRAINSHARE_BYTIME_FILENAME);
            FloatMatrixIO.writeAsCSV(matrices.trainDistanceShareMatrix, outputDirectory + "/" + PT_TRAINSHARE_BYDISTANCE_FILENAME);
        }

        // calc BEELINE matrices
        log.info("calc beeline distance matrix");
        FloatMatrix<String> beelineMatrix = BeelineDistanceMatrix.calculateBeelineDistanceMatrix(zonesById, coordsPerZone, numberOfThreads);

        log.info("write beeline distance matrix to " + outputDirectory);
        FloatMatrixIO.writeAsCSV(beelineMatrix, outputDirectory + "/" + BEELINE_DISTANCE_FILENAME);
    }

    private static <T> void combineMatrices(FloatMatrix<T> matrix1, FloatMatrix<T> matrix2) {
        Set<T> ids = matrix2.id2index.keySet();
        for (T fromId : ids) {
            for (T toId : ids) {
                float value2 = matrix2.get(fromId, toId);
                matrix1.add(fromId, toId, value2);
            }
        }
    }

    private static String findZone(Coord coord, Map<String, SimpleFeature> zones) {
        Point pt = GEOMETRY_FACTORY.createPoint(new Coordinate(coord.getX(), coord.getY()));
        for (Map.Entry<String, SimpleFeature> e : zones.entrySet()) {
            SimpleFeature f = e.getValue();
            Geometry geom = (Geometry) f.getDefaultGeometry();
            if (pt.intersects(geom)) {
                return e.getKey();
            }
        }
        return null;
    }

    private static class WeightedFacility {
        Coord coord;
        double weight;

        public WeightedFacility(Coord coord, double weight) {
            this.coord = coord;
            this.weight = weight;
        }
    }

}
