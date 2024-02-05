package ch.sbb.matsim.preparation;

import ch.sbb.matsim.RunSBB;
import ch.sbb.matsim.routing.pt.raptor.RaptorUtils;
import ch.sbb.matsim.routing.pt.raptor.SwissRailRaptorData;
import org.apache.logging.log4j.LogManager;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.config.Config;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.geometry.CoordUtils;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt.transitSchedule.api.TransitScheduleReader;
import org.matsim.pt.transitSchedule.api.TransitScheduleWriter;

public class TransferTimeChecker {


    public static void addAdditionalTransferTimes(TransitSchedule schedule, Network network, Config config) {

        double maxWalkDistance = config.transitRouter().getMaxBeelineWalkConnectionDistance();
        double walkSpeed = config.routing().getTeleportedModeParams().get(TransportMode.walk).getTeleportedModeSpeed();
        double distanceFactor = config.routing().getTeleportedModeParams().get(TransportMode.walk).getBeelineDistanceFactor();
        SwissRailRaptorData data = SwissRailRaptorData.create(schedule, null, RaptorUtils.createStaticConfig(config), network, null);
        int count = 0;
        for (var stop : schedule.getFacilities().values()) {
            double intraStopMTT = schedule.getMinimalTransferTimes().get(stop.getId(), stop.getId(), 0);

            Coord coord = stop.getCoord();
            var stopCandidates = data.findNearbyStops(coord.getX(), coord.getY(), maxWalkDistance);
            for (var otherStop : stopCandidates) {
                if (Double.isNaN(schedule.getMinimalTransferTimes().get(stop.getId(), otherStop.getId()))) {
                    double otherStopintraStopMTT = schedule.getMinimalTransferTimes().get(otherStop.getId(), otherStop.getId(), 0);
                    double relevantIntraStopTT = Math.max(intraStopMTT, otherStopintraStopMTT);
                    double walkTime = CoordUtils.calcEuclideanDistance(stop.getCoord(), otherStop.getCoord()) * distanceFactor / walkSpeed;
                    double mtt = relevantIntraStopTT + walkTime;
                    schedule.getMinimalTransferTimes().set(stop.getId(), otherStop.getId(), mtt);
                    schedule.getMinimalTransferTimes().set(otherStop.getId(), stop.getId(), mtt);
                    count++;
                }
            }
        }
        LogManager.getLogger(TransferTimeChecker.class).info("Adjusted " + 2 * count + " stop-to-stop connections");

    }

    public static void addAdditionalTransferTimes(Scenario scenario) {
        addAdditionalTransferTimes(scenario.getTransitSchedule(), scenario.getNetwork(), scenario.getConfig());
    }


    public static void main(String[] args) {
        String scheduleFile = args[0];
        String networkFile = args[1];
        String configFile = args[2];
        String outputScheduleFile = args[3];
        Config config = RunSBB.buildConfig(configFile);
        config.transitRouter().setMaxBeelineWalkConnectionDistance(300);
        Scenario scenario = ScenarioUtils.createScenario(config);
        new TransitScheduleReader(scenario).readFile(scheduleFile);
        new MatsimNetworkReader(scenario.getNetwork()).readFile(networkFile);
        addAdditionalTransferTimes(scenario.getTransitSchedule(), scenario.getNetwork(), config);
        new TransitScheduleWriter(scenario.getTransitSchedule()).writeFile(outputScheduleFile);
    }
}
