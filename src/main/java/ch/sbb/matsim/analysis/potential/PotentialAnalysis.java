package ch.sbb.matsim.analysis.potential;

import ch.sbb.matsim.analysis.skims.StreamingFacilities;
import ch.sbb.matsim.csv.CSVWriter;
import ch.sbb.matsim.routing.pt.raptor.RaptorParameters;
import ch.sbb.matsim.routing.pt.raptor.RaptorStaticConfig;
import ch.sbb.matsim.routing.pt.raptor.RaptorUtils;
import ch.sbb.matsim.routing.pt.raptor.SwissRailRaptor;
import ch.sbb.matsim.routing.pt.raptor.SwissRailRaptorData;
import ch.sbb.matsim.zones.Zone;
import ch.sbb.matsim.zones.Zones;
import ch.sbb.matsim.zones.ZonesLoader;
import ch.sbb.matsim.zones.ZonesQueryCache;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.algorithms.TransportModeNetworkFilter;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.geometry.CoordUtils;
import org.matsim.core.utils.misc.Counter;
import org.matsim.facilities.ActivityFacility;
import org.matsim.facilities.MatsimFacilitiesReader;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitRouteStop;
import org.matsim.pt.transitSchedule.api.TransitScheduleReader;
import org.matsim.pt.transitSchedule.api.TransitScheduleWriter;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;

public class PotentialAnalysis {
    private static final Logger log = Logger.getLogger(PotentialAnalysis.class);
    private static final String COL_X = "X";
    private static final String COL_Y = "Y";
    private static final String COL_ID = "ID";
    private static final String COL_ACC_CAR = "ACCESSIBILITY_CAR";
    private static final String COL_ACC_RATIO = "ACCESSIBILITY_RATIO";
    private static final String COL_ACC_PT = "ACCESSIBILITY_PT";
    private static final String COL_ACC_MULTI = "ACCESSIBILITY_MULTI";
    private static final String COL_TIME_TO_STATION = "time_to_closest_station";
    private static final String[] COLUMNS = {COL_X, COL_Y, COL_ID, COL_ACC_CAR, COL_ACC_MULTI, COL_ACC_PT, COL_TIME_TO_STATION, COL_ACC_RATIO};

    public static void main(String[] args) throws IOException {
        System.setProperty("matsim.preferLocalDtds", "true");

        String zonesShapeFilename = args[0];
        String facilitiesFilename = "\\\\Filer16L\\P-V160L\\SIMBA.A11244\\90_Persoenlich\\u222223\\facilities_to_stations.xml.gz"; //args[1];
        String networkFilename = args[2];
        String transitScheduleFilename = args[3];
        //String eventsFilename = args[3].equals("-") ? null : args[3];
        //String outputDirectory = args[4];


        PotentialAnalysis potentialAnalysis = new PotentialAnalysis();
        potentialAnalysis.run(transitScheduleFilename, networkFilename, facilitiesFilename, zonesShapeFilename);


    }


    private static Collection<TransitStopFacility> findStopCandidates(Coord coord, SwissRailRaptor raptor, RaptorParameters parameters) {
        Collection<TransitStopFacility> stops = raptor.getUnderlyingData().findNearbyStops(coord.getX(), coord.getY(), parameters.getSearchRadius());
        if (stops.isEmpty()) {
            TransitStopFacility nearest = raptor.getUnderlyingData().findNearestStop(coord.getX(), coord.getY());
            double nearestStopDistance = CoordUtils.calcEuclideanDistance(coord, nearest.getCoord());
            stops = raptor.getUnderlyingData().findNearbyStops(coord.getX(), coord.getY(), nearestStopDistance + parameters.getExtensionRadius());
        }
        return stops;
    }


    private void zone2facility(String shapfile, Collection<? extends ActivityFacility> facilities) {

        Zones zones = new ZonesQueryCache(ZonesLoader.loadZones("zones", shapfile, "NO"));

        for (ActivityFacility facility : facilities) {
            int id = (int) Double.parseDouble(facility.getAttributes().getAttribute("tZone").toString());
            Zone zone = zones.getZone(Id.create(id, Zone.class));

            double acc_car = Double.parseDouble(zone.getAttribute("ACCESSIB~7").toString());
            double acc_pt = Double.parseDouble(zone.getAttribute("ACCESSI~18").toString());

            facility.getAttributes().putAttribute(COL_ACC_CAR, acc_car);
            facility.getAttributes().putAttribute(COL_ACC_MULTI, zone.getAttribute("ACCESSI~12"));
            facility.getAttributes().putAttribute(COL_ACC_PT, acc_pt);
            facility.getAttributes().putAttribute(COL_ACC_RATIO, acc_pt / acc_car);

        }


    }

    private void zone2stations(String shapfile, Collection<? extends TransitStopFacility> facilities) {

        Zones zones = new ZonesQueryCache(ZonesLoader.loadZones("zones", shapfile, "NO"));

        for (TransitStopFacility facility : facilities) {
            Zone zone = zones.findZone(facility.getCoord().getX(), facility.getCoord().getY());
            if (zone != null) {
                double acc_car = Double.parseDouble(zone.getAttribute("ACCESSIB~7").toString());
                double acc_pt = Double.parseDouble(zone.getAttribute("ACCESSI~18").toString());

                facility.getAttributes().putAttribute(COL_ACC_CAR, acc_car);
                facility.getAttributes().putAttribute(COL_ACC_MULTI, zone.getAttribute("ACCESSI~12"));
                facility.getAttributes().putAttribute(COL_ACC_PT, acc_pt);
                facility.getAttributes().putAttribute(COL_ACC_RATIO, acc_pt / acc_car);
            }
        }


    }


    private void loadFacilities(Scenario scenario, String facilitiesFilename) {


        Counter facCounter = new Counter("#");
        scenario.getActivityFacilities();
        new MatsimFacilitiesReader(null, null, new StreamingFacilities(
                f -> {
                    facCounter.incCounter();
                    scenario.getActivityFacilities().addActivityFacility(f);
                }
        )).readFile(facilitiesFilename);
        facCounter.printCounter();


    }

    private void loadTransit(Scenario scenario, String transitScheduleFilename) {
        Config config = ConfigUtils.createConfig();
        Scenario scenario2 = ScenarioUtils.createScenario(config);
        (new TransitScheduleReader(scenario2)).readFile(transitScheduleFilename);

        for (TransitStopFacility facility : scenario2.getTransitSchedule().getFacilities().values()) {
            if (facility.getAttributes().getAttribute("01_Datenherkunft").equals("SBB_Simba")) {
                scenario.getTransitSchedule().addStopFacility(facility);
            }
        }


        Collection<TransitLine> transitLines = scenario2.getTransitSchedule().getTransitLines().values();
        for (TransitLine transitLine : transitLines) {
            log.info(transitLine);
            boolean remove = false;

            for (TransitRoute transitRoute : transitLine.getRoutes().values()) {
                for (TransitRouteStop stop : transitRoute.getStops()) {
                    if (!stop.getStopFacility().getAttributes().getAttribute("01_Datenherkunft").equals("SBB_Simba")) {
                        remove = true;
                    }
                }
            }
            if (!remove) {

                scenario.getTransitSchedule().addTransitLine(transitLine);
            }


        }

    }


    private void computeDurationToStation(Scenario scenario) {
        Config config = ConfigUtils.createConfig();


        RaptorStaticConfig raptorConfig = RaptorUtils.createStaticConfig(config);
        raptorConfig.setOptimization(RaptorStaticConfig.RaptorOptimization.OneToAllRouting);
        SwissRailRaptorData raptorData = SwissRailRaptorData.create(scenario.getTransitSchedule(), raptorConfig, scenario.getNetwork());
        RaptorParameters raptorParameters = RaptorUtils.createParameters(config);

        SwissRailRaptor raptor = new SwissRailRaptor(raptorData, null, null, null);

        Network carNetwork = NetworkUtils.createNetwork();
        (new TransportModeNetworkFilter(scenario.getNetwork())).filter(carNetwork, Collections.singleton("car"));

        PotentialAnalysisRouter potentialAnalysisRouter = new PotentialAnalysisRouter(carNetwork, 7 * 60 * 60);
        Counter facCounterProcess = new Counter("#");
        for (ActivityFacility facility : scenario.getActivityFacilities().getFacilities().values()) {
            double minimumTime = 999999999;
            TransitStopFacility closestFacility = null;
            facCounterProcess.incCounter();

            for (TransitStopFacility stopFacility : findStopCandidates(facility.getCoord(), raptor, raptorParameters)) {

                Leg leg = potentialAnalysisRouter.fetch(facility, stopFacility);
				if (leg.getTravelTime().seconds() < minimumTime || closestFacility == null) {
					closestFacility = stopFacility;
					minimumTime = leg.getTravelTime().seconds();
				}

            }
            facility.getAttributes().putAttribute("time_to_closest_station", minimumTime);

        }
        facCounterProcess.printCounter();


    }

    private void run(String transitScheduleFilename, String networkFilename, String facilitiesFilename, String shapefile) {

        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());

        this.loadFacilities(scenario, facilitiesFilename);

        //(new MatsimNetworkReader(scenario.getNetwork())).readFile(networkFilename);
        this.loadTransit(scenario, transitScheduleFilename);
        this.zone2stations(shapefile, scenario.getTransitSchedule().getFacilities().values());
        new TransitScheduleWriter(scenario.getTransitSchedule()).writeFile("\\\\Filer16L\\P-V160L\\SIMBA.A11244\\40_Projekte\\20190515_MOBi_Anwendung_NMD\\20_Arbeiten\\10_PotentialAnalyse\\01_Daten\\stations\\transitSchedule.xml.gz");

        //this.computeDurationToStation(scenario);
        this.zone2facility(shapefile, scenario.getActivityFacilities().getFacilities().values());

        String filename = "\\\\Filer16L\\P-V160L\\SIMBA.A11244\\90_Persoenlich\\u222223\\facilities_to_stations2.csv";
        this.writeCSV(filename, scenario.getActivityFacilities().getFacilities().values());
        //new FacilitiesWriter(scenario.getActivityFacilities()).write();

    }


    private void writeCSV(String filename, Collection<? extends ActivityFacility> facilities) {
        log.info("write facilities to " + filename);
        try (CSVWriter writer = new CSVWriter("", COLUMNS, filename)) {
            for (ActivityFacility facility : facilities) {
                writer.set(COL_ID, facility.getId().toString());
                writer.set(COL_X, Double.toString(facility.getCoord().getX()));
                writer.set(COL_Y, Double.toString(facility.getCoord().getY()));
                writer.set(COL_ACC_CAR, facility.getAttributes().getAttribute(COL_ACC_CAR).toString());
                writer.set(COL_ACC_PT, facility.getAttributes().getAttribute(COL_ACC_PT).toString());
                writer.set(COL_ACC_MULTI, facility.getAttributes().getAttribute(COL_ACC_MULTI).toString());
                writer.set(COL_ACC_RATIO, facility.getAttributes().getAttribute(COL_ACC_RATIO).toString());
                writer.set(COL_TIME_TO_STATION, facility.getAttributes().getAttribute(COL_TIME_TO_STATION).toString());
                writer.writeRow();
            }
        } catch (IOException e) {
            log.error("Could not write facilities. " + e.getMessage(), e);
        }


    }

}
