package ch.sbb.matsim.projects.bnfri;

import ch.sbb.matsim.RunSBB;
import ch.sbb.matsim.analysis.modalsplit.MSVariables;
import ch.sbb.matsim.config.variables.SBBModes;
import ch.sbb.matsim.mavi.MaviHelper;
import ch.sbb.matsim.preparation.TransferTimeChecker;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.core.network.io.NetworkWriter;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.pt.transitSchedule.api.TransitScheduleReader;
import org.matsim.pt.transitSchedule.api.TransitScheduleWriter;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

public class AdjustScheduleAttributes {

    public static void main(String[] args) {
        Scenario mobiScenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        Scenario gtfsScenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
//        String date = "2025-07-01";
        String date = "2025-12-09";

        new TransitScheduleReader(mobiScenario).readFile("/Volumes/mobi/40_Projekte/20241104_MOBi_5.1/pt/NPVM2023/output/transitSchedule.xml.gz");
        String basefolder = "/Volumes/mobi/40_Projekte/20251105_decalage/pt/";
        Config config = RunSBB.buildConfig("/Volumes/mobi/40_Projekte/20251105_decalage/sim/ref/simulation_config.xml_orig");
        new TransitScheduleReader(gtfsScenario).readFile(basefolder + date + "/raw/transitSchedule.xml.gz");
        new MatsimNetworkReader(gtfsScenario.getNetwork()).readFile(basefolder + date + "/raw/network.xml.gz");
        MaviHelper.cleanStops(gtfsScenario.getTransitSchedule());
        gtfsScenario.getTransitSchedule().getTransitLines().values().stream().flatMap(transitLine -> transitLine.getRoutes().values().stream()).
                forEach(transitRoute -> {
                    String modeName = transitRoute.getTransportMode();
                    String newMode;
                    if (modeName.toLowerCase().contains("rail")) {
                        newMode = "rail";
                    } else if (modeName.toLowerCase().contains("bus")) {
                        newMode = "bus";
                    } else if (modeName.toLowerCase().contains("train")) {
                        newMode = "rail";
                    } else if (modeName.toLowerCase().contains("tram")) {
                        newMode = "tram";
                    } else if (modeName.toLowerCase().contains("metro")) {
                        newMode = "tram";
                    } else {
                        newMode = "other";
                    }
                    transitRoute.setTransportMode(newMode);
                    transitRoute.getRoute().getLinkIds().forEach(linkId -> {
                                var allModes = new HashSet<>(gtfsScenario.getNetwork().getLinks().get(linkId).getAllowedModes());
                                allModes.add(newMode);
                                allModes.remove(SBBModes.PT);
                                gtfsScenario.getNetwork().getLinks().get(linkId).setAllowedModes(allModes);
                            }
                    );

                });
        var transferTimesIterator = gtfsScenario.getTransitSchedule().getMinimalTransferTimes().iterator();
        int removedTransferTimes = 0;
        while (transferTimesIterator.hasNext()) {
            transferTimesIterator.next();
            if (!gtfsScenario.getTransitSchedule().getFacilities().containsKey(transferTimesIterator.getFromStopId())) {
                gtfsScenario.getTransitSchedule().getMinimalTransferTimes().remove(transferTimesIterator.getFromStopId(), transferTimesIterator.getToStopId());
                removedTransferTimes++;
            } else if (!gtfsScenario.getTransitSchedule().getFacilities().containsKey(transferTimesIterator.getToStopId())) {
                gtfsScenario.getTransitSchedule().getMinimalTransferTimes().remove(transferTimesIterator.getFromStopId(), transferTimesIterator.getToStopId());
                removedTransferTimes++;
            }
        }
        System.out.println("Removed " + removedTransferTimes + " transfer times that were not matching any stop facility.");
        Map<String, TransitStopFacility> facilityNameToId = new HashMap<>();
        for (var transitStopFacility : mobiScenario.getTransitSchedule().getFacilities().values()) {
            String name = transitStopFacility.getName().trim().toLowerCase();
            if (!facilityNameToId.containsKey(name)) {
                facilityNameToId.put(name, transitStopFacility);
            }
        }

        gtfsScenario.getTransitSchedule().getFacilities().values().forEach(
                transitStopFacility -> {
                    String name = transitStopFacility.getName();
                    if (facilityNameToId.containsKey(name.trim().toLowerCase())) {
                        TransitStopFacility facility = facilityNameToId.get(name.trim().toLowerCase());
                        transitStopFacility.getAttributes().putAttribute("mobiId", facility.getId().toString());
                        facility.getAttributes().getAsMap().forEach(
                                (key, value) -> transitStopFacility.getAttributes().putAttribute(key, value)
                        );
                    }
                    if (transitStopFacility.getAttributes().getAttribute("carFeederAccessible") == null) {
                        transitStopFacility.getAttributes().putAttribute("carFeederAccessible", "0");
                        transitStopFacility.getAttributes().putAttribute("bikeFeederAccessible", "0");
                        transitStopFacility.getAttributes().putAttribute("rideFeederAccessible", "0");
                    }
                    if (transitStopFacility.getAttributes().getAttribute(MSVariables.STOP_NO) == null) {
                        transitStopFacility.getAttributes().putAttribute(MSVariables.STOP_NO, transitStopFacility.getId().toString());

                    }
                }
        );
        TransferTimeChecker.addAdditionalTransferTimes(gtfsScenario.getTransitSchedule(), gtfsScenario.getNetwork(), config);
        new TransitScheduleWriter(gtfsScenario.getTransitSchedule()).writeFile(basefolder + date + "/transitSchedule.xml.gz");
        new NetworkWriter(gtfsScenario.getNetwork()).write(basefolder + date + "/transitNetwork.xml.gz");
    }
}
