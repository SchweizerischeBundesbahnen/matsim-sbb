package ch.sbb.matsim.analysis.tripsandlegsanalysis;

import org.matsim.analysis.TripsAndLegsCSVWriter;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.pt.routes.TransitPassengerRoute;

import jakarta.inject.Inject;

import java.util.Collections;
import java.util.List;

public class SBBLegsExtension implements TripsAndLegsCSVWriter.CustomLegsWriterExtension {

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
