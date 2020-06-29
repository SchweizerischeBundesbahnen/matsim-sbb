package ch.sbb.matsim.rideshare.utils;

import ch.sbb.matsim.config.variables.SBBModes;
import ch.sbb.matsim.zones.Zones;
import ch.sbb.matsim.zones.ZonesLoader;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.contrib.dvrp.fleet.DvrpVehicle;
import org.matsim.contrib.dvrp.fleet.DvrpVehicleSpecification;
import org.matsim.contrib.dvrp.fleet.FleetWriter;
import org.matsim.contrib.dvrp.fleet.ImmutableDvrpVehicleSpecification;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.filter.NetworkFilterManager;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.pt.transitSchedule.api.TransitScheduleReader;
import org.matsim.pt.transitSchedule.api.TransitScheduleWriter;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;

public class AddIntermodalScheduleAttributesAndCreateFleetVehicles {

    public static final String DRTFEEDER = "drtfeeder";
    public static final String DRTFEEDER_LINK_ID = "drtfeeder_linkId";

    public static final String OUTPUT_SCHEDULE = "\\\\k13536\\mobi\\40_Projekte\\20190913_Ridesharing\\sim\\bern\\input\\schedule_feeder_BernBhf.xml.gz";
    public static final String INPUT_SCHEDULE = "\\\\k13536\\mobi\\40_Projekte\\20190913_Ridesharing\\sim\\bern\\input\\schedule.xml.gz";
    public static final String INPUT_NETWORK = "\\\\k13536\\mobi\\40_Projekte\\20190913_Ridesharing\\sim\\bern\\input\\network.xml.gz";
    public static final String INPUT_SHAPE = "\\\\k13536\\mobi\\40_Projekte\\20190805_ScenarioCutter\\20190805_zones\\neuenburg\\neuenburg-agglo.shp";
    private static final int numberOfVehiclesPerStop = 100;
    private static final int seatsPerVehicle = 4;
    private static final double operationStartTime = 0;
    private static final double operationEndTime = 30 * 60 * 60;
    private static final String OUTPUT_FLEETVEHICLES = "\\\\k13536\\mobi\\40_Projekte\\20190913_Ridesharing\\sim\\bern\\input\\fleetvehicles_bernbhf100.xml";
    private static List<Id<TransitStopFacility>> stops = Arrays.asList(Id.create(1311, TransitStopFacility.class));

    //    private static List<Id<TransitStopFacility>> stops = Arrays.asList(new Id[]{
//            Id.create(3209, TransitStopFacility.class), //Wankdorf
//            Id.create(3167, TransitStopFacility.class), //Worblaufen
//            Id.create(725, TransitStopFacility.class), //Bruennen
//            Id.create(2380, TransitStopFacility.class), //Niederwangen
//            Id.create(2055, TransitStopFacility.class), //Koenitz
//            Id.create(3164, TransitStopFacility.class), //Wabern
//            Id.create(2436, TransitStopFacility.class), //Ostermundigen
//            Id.create(1897, TransitStopFacility.class)} //Guemligen
//
//    );

    public static void main(String[] args) {
        Zones zones = ZonesLoader.loadZones("id", INPUT_SHAPE, "ID");
        //Envelope envelope = zones.getZone(Id.create("648701006", Zone.class)).getEnvelope();
        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        new TransitScheduleReader(scenario).readFile(INPUT_SCHEDULE);
        new MatsimNetworkReader(scenario.getNetwork()).readFile(INPUT_NETWORK);
        NetworkFilterManager networkFilterManager = new NetworkFilterManager(scenario.getNetwork());
		networkFilterManager.addLinkFilter(f -> f.getAllowedModes().contains(SBBModes.CAR));
        Network filteredNet = networkFilterManager.applyFilters();
        Set<DvrpVehicleSpecification> vehicleSpecifications = new HashSet<>();
        final int[] i = {0};
        scenario.getTransitSchedule().getFacilities().values().stream()
                .filter(transitStopFacility -> stops.contains(transitStopFacility.getId()))
                .forEach(transitStopFacility ->
                {
                    transitStopFacility.getAttributes().putAttribute(DRTFEEDER, 1);
                    Id<Link> stopLink = NetworkUtils.getNearestLink(filteredNet, transitStopFacility.getCoord()).getId();
                    transitStopFacility.getAttributes().putAttribute(DRTFEEDER_LINK_ID, stopLink.toString());

                    for (int z = 0; z < numberOfVehiclesPerStop; z++) {
                        vehicleSpecifications.add(ImmutableDvrpVehicleSpecification.newBuilder()
                                .id(Id.create(DRTFEEDER + i[0], DvrpVehicle.class))
                                .startLinkId(stopLink)
                                .capacity(seatsPerVehicle)
                                .serviceBeginTime(operationStartTime)
                                .serviceEndTime(operationEndTime)
                                .build());
                        i[0]++;
                    }
                });


        new TransitScheduleWriter(scenario.getTransitSchedule()).writeFile(OUTPUT_SCHEDULE);
        new FleetWriter(vehicleSpecifications.stream().sorted(Comparator.comparing(v -> v.getId().toString()))).write(OUTPUT_FLEETVEHICLES);
    }
}
