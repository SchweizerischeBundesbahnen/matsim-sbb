package ch.sbb.matsim.visumdistribution;

import ch.sbb.matsim.routing.pt.raptor.RaptorRoute;
import ch.sbb.matsim.routing.pt.raptor.SwissRailRaptorCore.TravelInfo;
import java.util.HashMap;
import java.util.Map;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;

public class MyTravelInfo {

    Map<String, Double> fastedConnection = new HashMap<>();
    private final String id;
    private final RaptorRoute raptorRoute;


    MyTravelInfo(TravelInfo travelInfo, TransitStopFacility transitStopFacility) {
        StringBuilder stringBuilder = new StringBuilder();
        this.id = stringBuilder.append(travelInfo.departureStop).append(transitStopFacility.getId()).append(travelInfo.ptDepartureTime).append(travelInfo.ptTravelTime).toString();
        this.raptorRoute = travelInfo.getRaptorRoute();
    }



}
