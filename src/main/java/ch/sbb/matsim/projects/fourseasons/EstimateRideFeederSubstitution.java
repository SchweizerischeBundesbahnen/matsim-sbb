package ch.sbb.matsim.projects.fourseasons;

import ch.sbb.matsim.analysis.tripsandlegsanalysis.RailTripsAnalyzer;
import ch.sbb.matsim.config.variables.SBBModes;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.core.utils.collections.Tuple;
import org.matsim.pt.routes.TransitPassengerRoute;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class EstimateRideFeederSubstitution {

    private final RailTripsAnalyzer railTripsAnalyzer;
    private final List<Id<TransitStopFacility>> stopsCandidateList;
    private final Scenario scenario;
    private Map<Id<TransitStopFacility>, Long> flowsAtStops;

    public EstimateRideFeederSubstitution(Scenario scenario, List<Id<TransitStopFacility>> stopsCandidateList) {
        this.railTripsAnalyzer = new RailTripsAnalyzer(scenario.getTransitSchedule(), scenario.getNetwork());
        this.stopsCandidateList = stopsCandidateList;
        this.scenario = scenario;
        flowsAtStops = calculateFlowPassingThroughStops();
    }

    public static void main(String[] args) {

    }

    private void run() {
        for (var stop : this.stopsCandidateList) {
            var linesAtStop = railTripsAnalyzer.getTransitLinesAndRoutesAtStop(stop);
            Tuple<List<TripStructureUtils.Trip>, List<TripStructureUtils.Trip>> trips = findRailTripsStartingOrEndingAtStop(stop);
            //double flowAtStop = getFlowPassingThroughStops(stop);

        }
    }

    private Tuple<List<TripStructureUtils.Trip>, List<TripStructureUtils.Trip>> findRailTripsStartingOrEndingAtStop(Id<TransitStopFacility> stop) {
        List<TripStructureUtils.Trip> starting = new ArrayList<>();
        List<TripStructureUtils.Trip> ending = new ArrayList<>();
        return new Tuple<>(starting, ending);

    }

    private Map<Id<TransitStopFacility>, Long> calculateFlowPassingThroughStops() {
        Map<Id<Link>, Id<TransitStopFacility>> stopLinks = stopsCandidateList.stream().collect(Collectors.toMap(stopFacilityId -> scenario.getTransitSchedule().getFacilities().get(stopFacilityId).getLinkId(), stopFacilityId -> stopFacilityId));
        //Map<Id<Link>,MutableInt>
        return flowsAtStops = this.scenario.getPopulation().getPersons().values().stream()
                .map(person -> person.getSelectedPlan())
                .flatMap(plan -> TripStructureUtils.getLegs(plan).stream())
                .filter(leg -> leg.getMode().equals(SBBModes.PT))
                .map(leg -> (TransitPassengerRoute) leg.getRoute())
                .flatMap(transitPassengerRoute -> railTripsAnalyzer.getPtLinkIdsTraveledOnExludingAccessEgressStop(transitPassengerRoute).stream())
                .filter(linkId -> stopLinks.containsKey(linkId))
                .map(linkId -> stopLinks.get(linkId))
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

    }

}
