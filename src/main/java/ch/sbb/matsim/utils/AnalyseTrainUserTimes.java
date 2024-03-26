package ch.sbb.matsim.utils;

import ch.sbb.matsim.analysis.tripsandlegsanalysis.RailTripsAnalyzer;
import ch.sbb.matsim.config.variables.SBBModes;
import ch.sbb.matsim.config.variables.Variables;
import ch.sbb.matsim.zones.Zones;
import ch.sbb.matsim.zones.ZonesCollection;
import ch.sbb.matsim.zones.ZonesLoader;
import org.apache.commons.lang3.mutable.MutableDouble;
import org.apache.commons.lang3.mutable.MutableInt;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.core.population.io.StreamingPopulationReader;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.pt.routes.TransitPassengerRoute;
import org.matsim.pt.transitSchedule.api.TransitScheduleReader;

public class AnalyseTrainUserTimes {

    public static void main(String[] args) {
        //String experiencedPlansFile = "C:\\devsbb\\code\\matsim-sbb\\test\\output\\mobi-33-test\\MOBI33IT.output_experienced_plans.xml";
        String experiencedPlansFile = "C:\\devsbb\\M332017.7.output_experienced_plans.xml.gz";
        ZonesCollection zonesCollection = new ZonesCollection();
        Zones zones = ZonesLoader.loadZones("zones", "\\\\wsbbrz0283\\mobi\\50_Ergebnisse\\MOBi_4.0\\2017\\plans\\3.3.2017.7.100pct\\mobi-zones.shp");
        zonesCollection.addZones(zones);
        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        new TransitScheduleReader(scenario).readFile("\\\\wsbbrz0283\\mobi\\50_Ergebnisse\\MOBi_4.0\\2017\\sim\\3.3.2017.7.50pct\\output_slice0\\M332017.7.output_transitSchedule.xml.gz");
        new MatsimNetworkReader(scenario.getNetwork()).readFile("\\\\wsbbrz0283\\mobi\\50_Ergebnisse\\MOBi_4.0\\2017\\sim\\3.3.2017.7.50pct\\output_slice0\\M332017.7.output_network.xml.gz");

        MutableInt trainTrips = new MutableInt();
        MutableDouble inVehicleTrainTravelTime = new MutableDouble();
        MutableDouble totalTravelTime = new MutableDouble();
        MutableDouble accessEgressTime = new MutableDouble();
        MutableDouble waitTime = new MutableDouble();
        MutableDouble trainTransferTime = new MutableDouble();


        RailTripsAnalyzer railTripsAnalyzer = new RailTripsAnalyzer(scenario.getTransitSchedule(), scenario.getNetwork(), zonesCollection);

        StreamingPopulationReader reader = new StreamingPopulationReader(scenario);
        reader.addAlgorithm(person -> {
            Plan plan = person.getSelectedPlan();
            TripStructureUtils.getTrips(plan).stream()
                    .filter(trip -> railTripsAnalyzer.getOriginDestination(trip) != null)
                    .filter(trip -> isDomestic(trip, zones))
                    .forEach(trip -> {
                        double travelTime = trip.getDestinationActivity().getStartTime().seconds() - trip.getOriginActivity().getEndTime().seconds();
                        totalTravelTime.add(travelTime);
                        var trainSegment = railTripsAnalyzer.getOriginDestination(trip);
                        trainTrips.increment();
                        for (Leg leg : trip.getLegsOnly()) {
                            boolean trainTripStarted = false;
                            boolean trainTripComplete = false;
                            if (!trainTripStarted && leg.getRoute().getEndLinkId().toString().equals("pt_" + trainSegment.getFirst().toString())) {
                                trainTripStarted = true;
                            }
                            if (trainTripStarted && !trainTripComplete && leg.getRoute().getEndLinkId().toString().equals("pt_" + trainSegment.getSecond().toString())) {
                                trainTripComplete = true;
                            }
                            if (!leg.getMode().equals(SBBModes.PT)) {
                                if (!trainTripStarted || trainTripComplete)
                                    accessEgressTime.add(leg.getTravelTime().seconds());
                                else trainTransferTime.add(leg.getTravelTime().seconds());
                            } else {
                                TransitPassengerRoute route = (TransitPassengerRoute) leg.getRoute();
                                if (railTripsAnalyzer.isRailLine(route.getLineId())) {
                                    double waitT = ((Double) leg.getAttributes().getAttribute("enterVehicleTime")) - leg.getDepartureTime().seconds();
                                    double ivtt = leg.getTravelTime().seconds() - waitT;
                                    waitTime.add(waitT);
                                    inVehicleTrainTravelTime.add(ivtt);
                                }
                            }

                        }
                    });
        });
        reader.readFile(experiencedPlansFile);
        System.out.println("Train Trips:" + trainTrips.intValue() * 4);
        System.out.println("inVehicleTrainTravelTime:" + inVehicleTrainTravelTime.doubleValue() * 4);
        System.out.println("accessEgressTime:" + accessEgressTime.doubleValue() * 4);
        System.out.println("trainWaitTime:" + waitTime.doubleValue() * 4);
        System.out.println("trainTransferTime:" + trainTransferTime.doubleValue() * 4);
    }

    private static boolean isDomestic(TripStructureUtils.Trip trip, Zones zones) {
        var startZone = zones.findZone(trip.getOriginActivity().getCoord());
        if (startZone != null) {
            if (Variables.isSwissZone(startZone.getId())) {
                var endZone = zones.findZone(trip.getOriginActivity().getCoord());
                if (endZone != null) {
                    return Variables.isSwissZone(endZone.getId());
                }

            }
        }

        return false;
    }
}
