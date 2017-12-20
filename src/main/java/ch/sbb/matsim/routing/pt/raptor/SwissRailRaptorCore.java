/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2017.
 */

package ch.sbb.matsim.routing.pt.raptor;

import java.util.Arrays;
import java.util.List;

/**
 * The actual RAPTOR implementation, based on Delling et al, Round-Based Public Transit Routing".
 *
 * This class is <b>NOT</b> thread-safe due to the use of internal state during the route calculation.
 *
 * @author mrieser / SBB
 */
public class SwissRailRaptorCore {

    private final SwissRailRaptorData data;

    private double arrivalTimesAtStops[];

    public SwissRailRaptorCore(SwissRailRaptorData data) {
        this.data = data;
        this.arrivalTimesAtStops = new double[data.countStopFacilities];
    }

    public RaptorRoute calcLeastCostRoute(double depTime, List<InitialStop> accessStops, List<InitialStop> egressStops) {
        Arrays.fill(this.arrivalTimesAtStops, Double.POSITIVE_INFINITY);
        // TODO
        return null;
    }

    public RaptorRoute calcLeastCostRoute(double earliestDepTime, double latestDepTime, List<InitialStop> accessStops, List<InitialStop> egressStops) {
        // TODO
        return null;
    }

    public List<RaptorRoute> calcParetoSet(double depTime, List<InitialStop> accessStops, List<InitialStop> egressStops) {
        // TODO
        return null;
    }

    public List<RaptorRoute> calcParetoSet(double earliestDepTime, double latestDepTime, List<InitialStop> accessStops, List<InitialStop> egressStops) {
        // TODO
        return null;
    }
}
