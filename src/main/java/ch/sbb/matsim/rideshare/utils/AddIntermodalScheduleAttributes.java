package ch.sbb.matsim.rideshare.utils;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.filter.NetworkFilterManager;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.pt.transitSchedule.api.TransitScheduleReader;
import org.matsim.pt.transitSchedule.api.TransitScheduleWriter;

public class AddIntermodalScheduleAttributes {

    public static final String DRTFEEDER = "drtfeeder";
    public static final String DRTFEEDER_LINK_ID = "drtfeeder_linkId";

    public static void main(String[] args) {
        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        new TransitScheduleReader(scenario).readFile("C:\\devsbb\\data\\0.01_neuenburg\\input\\NE.100.output_transitSchedule.xml.gz");
        new MatsimNetworkReader(scenario.getNetwork()).readFile("C:\\devsbb\\data\\0.01_neuenburg\\input\\NE.100.output_network.xml.gz");
        NetworkFilterManager networkFilterManager = new NetworkFilterManager(scenario.getNetwork());
        networkFilterManager.addLinkFilter(f -> f.getAllowedModes().contains(TransportMode.car));
        Network filteredNet = networkFilterManager.applyFilters();
        scenario.getTransitSchedule().getFacilities().values().stream()
                .filter(transitStopFacility -> String.valueOf(transitStopFacility.getAttributes().getAttribute("01_Datenherkunft")).equals("SBB_Simba"))
                .forEach(transitStopFacility ->
                {
                    transitStopFacility.getAttributes().putAttribute(DRTFEEDER, 1);
                    Id<Link> stopLink = NetworkUtils.getNearestLink(filteredNet, transitStopFacility.getCoord()).getId();
                    transitStopFacility.getAttributes().putAttribute(DRTFEEDER_LINK_ID, stopLink.toString());
                });


        new TransitScheduleWriter(scenario.getTransitSchedule()).writeFile("C:\\devsbb\\data\\0.01_neuenburg\\input\\transitSchedule_allStations.xml.gz");


        //  1460-177382 lcdf
    }
}
