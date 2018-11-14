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
import org.matsim.core.utils.gis.ShapeFileReader;
import org.matsim.core.utils.misc.Time;
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
    static final String PT_TRANSFERCOUNTS_FILENAME = "pt_transfercounts.csv.gz";
    static final String BEELINE_DISTANCE_FILENAME = "beeline_distances.csv.gz";
    static final String ZONE_LOCATIONS_FILENAME = "zone_coordinates.csv";

    public static void main(String[] args) throws IOException {
        System.setProperty("matsim.preferLocalDtds", "true");

        String zonesShapeFilename = "D:\\devsbb\\mrieser\\data\\npvm_2016\\NPVM_OberBez.shp";
        String zonesIdAttributeName = "ID";
        String networkFilename = "D:\\devsbb\\mrieser\\data\\raptorPerfTest2\\network.xml.gz";
        String transitScheduleFilename = "D:\\devsbb\\mrieser\\data\\raptorPerfTest2\\transitSchedule.xml.gz";
        String eventsFilename = null;
        String outputDirectory = "D:\\devsbb\\mrieser\\data\\indicators";
        int numberOfPointsPerZone = 5;
        int numberOfThreads = 8;
        double[] times = {
                Time.parseTime("08:00:00"),
                Time.parseTime("08:15:00"),
                Time.parseTime("08:30:00"),
                Time.parseTime("08:45:00")
        };

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

        TravelTime tt;
        if (eventsFilename != null) {
            log.info("extracting actual travel times from " + eventsFilename);
            TravelTimeCalculator ttc = TravelTimeCalculator.create(scenario.getNetwork(), config.travelTimeCalculator());
            EventsManager events =  EventsUtils.createEventsManager();
            events.addHandler(ttc);
            new MatsimEventsReader(events).readFile(eventsFilename);
            tt = ttc.getLinkTravelTimes();
        } else {
            tt = new FreeSpeedTravelTime();
            log.info("No events specified. Travel Times will be calculated with free speed travel times.");
        }

        TravelDisutility td = new OnlyTimeDependentTravelDisutility(tt);

        // define points per zone
        log.info("choose coordinates per zone...");

        Map<String, Coord[]> coordsPerZone = new HashMap<>();
        Random r = new Random(20180404L);
        for (Map.Entry<String, SimpleFeature> e : zonesById.entrySet()) {
            String zoneId = e.getKey();
            SimpleFeature f = e.getValue();
            if (f.getDefaultGeometry() != null) {
                Coord[] coords = new Coord[numberOfPointsPerZone];
                coordsPerZone.put(zoneId, coords);
                for (int i = 0; i < numberOfPointsPerZone; i++) {
                    coords[i] = Utils.getRandomCoordinateInFeature(f, r);
                }
            }
        }

        try (CSVWriter writer = new CSVWriter(null, new String[] {"ZONE", "PT_INDEX", "X", "Y"}, new File(outputDir, ZONE_LOCATIONS_FILENAME).getAbsolutePath())) {
            for (Map.Entry<String, Coord[]> e : coordsPerZone.entrySet()) {
                String zoneId = e.getKey();
                Coord[] coords = e.getValue();
                for (int i = 0; i < coords.length; i++) {
                    Coord coord = coords[i];
                    writer.set("ZONE", zoneId);
                    writer.set("PT_INDEX", Integer.toString(i));
                    writer.set("X", Double.toString(coord.getX()));
                    writer.set("Y", Double.toString(coord.getY()));
                }
            }
        }

        // calc MIV matrix

        log.info("extracting car-only network");
        final Network carNetwork = NetworkUtils.createNetwork();
        new TransportModeNetworkFilter(scenario.getNetwork()).filter(carNetwork, Collections.singleton(TransportMode.car));

        log.info("calc CAR matrix for " + Time.writeTime(times[0]));
        NetworkIndicators<String> netIndicators = NetworkTravelTimeMatrix.calculateTravelTimeMatrix(carNetwork, zonesById, coordsPerZone, times[0], tt, td, numberOfPointsPerZone, numberOfThreads);

        if (tt instanceof FreeSpeedTravelTime) {
            log.info("Do not calculate CAR matrices for other times as only freespeed is being used");
        } else {
            for (int i = 1; i < times.length; i++) {
                log.info("calc CAR matrices for " + Time.writeTime(times[i]));
                NetworkIndicators<String> indicators2 = NetworkTravelTimeMatrix.calculateTravelTimeMatrix(carNetwork, zonesById, coordsPerZone, times[i], tt, td, numberOfPointsPerZone, numberOfThreads);
                log.info("merge CAR matrices for " + Time.writeTime(times[i]));
                combineMatrices(netIndicators.travelTimeMatrix, indicators2.travelTimeMatrix);
                combineMatrices(netIndicators.distanceMatrix, indicators2.distanceMatrix);
            }
            log.info("re-scale CAR matrices after all data is merged.");
            netIndicators.travelTimeMatrix.multiply((float) (1.0 / times.length));
            netIndicators.distanceMatrix.multiply((float) (1.0 / times.length));
        }

        log.info("write CAR matrices to " + outputDirectory);
        FloatMatrixIO.writeAsCSV(netIndicators.travelTimeMatrix, outputDirectory + "/" + CAR_TRAVELTIMES_FILENAME);
        FloatMatrixIO.writeAsCSV(netIndicators.distanceMatrix, outputDirectory + "/" + CAR_DISTANCES_FILENAME);

        // calc PT matrices
        log.info("prepare PT Matrix calculation");
        RaptorStaticConfig raptorConfig = RaptorUtils.createStaticConfig(config);
        raptorConfig.setOptimization(RaptorStaticConfig.RaptorOptimization.OneToAllRouting);
        SwissRailRaptorData raptorData = SwissRailRaptorData.create(scenario.getTransitSchedule(), raptorConfig, scenario.getNetwork());
        RaptorParameters raptorParameters = RaptorUtils.createParameters(config);

        log.info("calc PT matrices for " + Time.writeTime(times[0]));
        PTTravelTimeMatrix.PtIndicators<String> matrices = PTTravelTimeMatrix.calculateTravelTimeMatrix(raptorData, zonesById, coordsPerZone, times[0], raptorParameters, numberOfThreads);

        for (int i = 1; i < times.length; i++) {
            log.info("calc PT matrices for " + Time.writeTime(times[i]));
            PTTravelTimeMatrix.PtIndicators<String> matrices2 = PTTravelTimeMatrix.calculateTravelTimeMatrix(raptorData, zonesById, coordsPerZone, times[i], raptorParameters, numberOfThreads);

            log.info("merge PT matrices for " + Time.writeTime(times[i]));
            combineMatrices(matrices.travelTimeMatrix, matrices2.travelTimeMatrix);
            combineMatrices(matrices.accessTimeMatrix, matrices2.accessTimeMatrix);
            combineMatrices(matrices.egressTimeMatrix, matrices2.egressTimeMatrix);
            combineMatrices(matrices.transferCountMatrix, matrices2.transferCountMatrix);
        }

        log.info("re-scale PT matrices after all data is merged.");
        matrices.travelTimeMatrix.multiply((float) (1.0 / times.length));
        matrices.accessTimeMatrix.multiply((float) (1.0 / times.length));
        matrices.egressTimeMatrix.multiply((float) (1.0 / times.length));
        matrices.transferCountMatrix.multiply((float) (1.0 / times.length));

        log.info("write PT matrices to " + outputDirectory);
        FloatMatrixIO.writeAsCSV(matrices.travelTimeMatrix, outputDirectory + "/" + PT_TRAVELTIMES_FILENAME);
        FloatMatrixIO.writeAsCSV(matrices.accessTimeMatrix, outputDirectory + "/" + PT_ACCESSTIMES_FILENAME);
        FloatMatrixIO.writeAsCSV(matrices.egressTimeMatrix, outputDirectory + "/" + PT_EGRESSTIMES_FILENAME);
        FloatMatrixIO.writeAsCSV(matrices.transferCountMatrix, outputDirectory + "/" + PT_TRANSFERCOUNTS_FILENAME);

        // calc BEELINE matrices
        log.info("calc beeline distance matrix");
        FloatMatrix<String> beelineMatrix = BeelineDistanceMatrix.calculateBeelineDistanceMatrix(zonesById, numberOfPointsPerZone, numberOfThreads);

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
}
