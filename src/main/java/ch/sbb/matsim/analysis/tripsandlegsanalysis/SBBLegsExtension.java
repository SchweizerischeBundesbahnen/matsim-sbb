package ch.sbb.matsim.analysis.tripsandlegsanalysis;

import jakarta.inject.Inject;
import org.matsim.analysis.TripsAndLegsWriter;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.pt.routes.TransitPassengerRoute;

import java.util.Collections;
import java.util.List;

public class SBBLegsExtension implements TripsAndLegsWriter.CustomLegsWriterExtension {

    private final RailTripsAnalyzer railTripsAnalyzer;

    @Inject
    public SBBLegsExtension(RailTripsAnalyzer railTripsAnalyzer) {
        this.railTripsAnalyzer = railTripsAnalyzer;
    }


    @Override
    public String[] getAdditionalLegHeader() {
        return new String[]{"fq_rail_distance"};
    }

    @Override
    public List<String> getAdditionalLegColumns(TripStructureUtils.Trip trip, Leg leg) {
        String fq_distance = "";
        if (leg.getRoute() instanceof TransitPassengerRoute) {
            TransitPassengerRoute route = (TransitPassengerRoute) leg.getRoute();
            if (railTripsAnalyzer.isRailLine(route.getLineId())) {
                fq_distance = Integer.toString((int) railTripsAnalyzer.getFQDistance(Collections.singletonList(route), true));
            }
        }
        return Collections.singletonList(fq_distance);
    }
}
