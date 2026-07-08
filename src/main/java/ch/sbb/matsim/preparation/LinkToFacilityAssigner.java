package ch.sbb.matsim.preparation;

import ch.sbb.matsim.config.variables.SBBModes;
import ch.sbb.matsim.config.variables.Variables;
import ch.sbb.matsim.routing.access.AccessEgressModule;
import ch.sbb.matsim.zones.Zone;
import ch.sbb.matsim.zones.Zones;
import ch.sbb.matsim.zones.ZonesLoader;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.contrib.common.util.DistanceUtils;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.NetworkConfigGroup;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.filter.NetworkFilterManager;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.facilities.ActivityFacilities;
import org.matsim.facilities.FacilitiesUtils;
import org.matsim.facilities.FacilitiesWriter;
import org.matsim.facilities.MatsimFacilitiesReader;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LinkToFacilityAssigner {

	 static void main(String[] args) {
        String facilityFile = args[0];
        String networkFile = args[1];
        String outputFile = args[2];
        String zonesFile = args[3];
        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        Zones zones = ZonesLoader.loadZones("", zonesFile);
        new MatsimFacilitiesReader(scenario).readFile(facilityFile);
        Network network = NetworkUtils.createNetwork();
        new MatsimNetworkReader(network).readFile(networkFile);
        run(scenario.getActivityFacilities(), network, scenario.getConfig() ,zones);
        new FacilitiesWriter(scenario.getActivityFacilities()).write(outputFile);

    }

	public static void run(ActivityFacilities facilities, Network network, Config config, Zones zones) {
        Network filteredNetwork = getAccessibleLinks(network, config.network());
        if (zones == null) {
            assignLinkToFacility(facilities, filteredNetwork);

        }
        else  {
            assignLinkInZoneToFacility(facilities, filteredNetwork, zones);
        }

    }

    public LinkToFacilityAssigner() {
    }

    private static void assignLinkInZoneToFacility(ActivityFacilities facilities, Network filteredNetwork, Zones zones) {
        Map<Id<Zone>, List<Link>> linksByZone = indexLinksByZone(filteredNetwork, zones);
        facilities.getFacilities().values().
                stream().filter(f -> f.getLinkId() == null)
                .forEach(f -> {
                    var zone = zones.findZone(f.getCoord());
                    if (zone != null) {
                        List<Link> linksInZone = linksByZone.get(zone.getId());
                        double minDistance = Double.MAX_VALUE;
                        Link bestLink = null;
                        if (linksInZone != null) {
                            for (Link link : linksInZone) {
                                double distanceSq = DistanceUtils.calculateSquaredDistance(link.getCoord(), f.getCoord());
                                if (distanceSq < minDistance) {
                                    minDistance = distanceSq;
                                    bestLink = link;
                                }
                            }
                        }
                        if(bestLink != null) {
                            FacilitiesUtils.setLinkID(f, bestLink.getId());
                        } else {
                            FacilitiesUtils.setLinkID(f, NetworkUtils.getNearestLink(filteredNetwork,
                                    f.getCoord()).getId());
                        }

                    } else {
                        FacilitiesUtils.setLinkID(f, NetworkUtils.getNearestLink(filteredNetwork,
                                f.getCoord()).getId());
                    }
                });

    }

    private static Map<Id<Zone>, List<Link>> indexLinksByZone(Network filteredNetwork, Zones zones) {
        Map<Id<Zone>, List<Link>> linksByZone = new HashMap<>();
        for (Link link : filteredNetwork.getLinks().values()) {
            Zone zone = AccessEgressModule.getZone(link, zones);
            if (zone != null) {
                linksByZone.computeIfAbsent(zone.getId(), ignored -> new ArrayList<>()).add(link);
            }
        }
        return linksByZone;
    }

    private static void assignLinkToFacility(ActivityFacilities facilities, Network network) {
        facilities.getFacilities().values().
                stream().filter(f -> f.getLinkId() == null)
                .forEach(f -> FacilitiesUtils.setLinkID(f, NetworkUtils.getNearestLink(network,
                        f.getCoord()).getId()));
    }


    public static Network getAccessibleLinks(Network network, NetworkConfigGroup networkConfigGroup) {
        NetworkFilterManager networkFilterManager = new NetworkFilterManager(network, networkConfigGroup);
        networkFilterManager.addLinkFilter(l -> (!String.valueOf(l.getAttributes().getAttribute(Variables.ACCESS_CONTROLLED)).equals("1")));
        networkFilterManager.addLinkFilter(l -> l.getAllowedModes().contains(SBBModes.CAR));
        return networkFilterManager.applyFilters();
    }


}


