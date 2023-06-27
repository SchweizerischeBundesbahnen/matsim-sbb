/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2018.
 */

package ch.sbb.matsim.utils;

import java.util.List;
import java.util.Set;

import jakarta.inject.Inject;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.core.config.Config;
import org.matsim.core.router.MainModeIdentifier;

/**
 * @author mrieser / SBB
 */
public class SBBIntermodalAwareRouterModeIdentifier implements MainModeIdentifier {

    private final Set<String> transitModes;

    @Inject
    public SBBIntermodalAwareRouterModeIdentifier(Config config) {
        this.transitModes = config.transit().getTransitModes();
    }

    /**
     * Intermodal trips can have a number of different legs and interaction activities, e.g.: non_network_walk | bike-interaction | bike | pt-interaction | transit-walk | pt-interaction | train |
     * pt-interaction | non_network_walk Thus, this main mode identifier uses the following heuristic to decide to which router mode a trip belongs: - if there is a leg with a pt mode (based on
     * config.transit().getTransitModes(), it returns that pt mode. - if there is only a leg with mode transit_walk, one of the configured transit modes is returned. - otherwise, the first mode not
     * being an non_network_walk or transit_walk.
     * <p>
     * The above comment is a little outdated since we introduced routing mode. However, with routing mode this MainModeIdentifier will no longer be used except for backward compatibility, i.e. update
     * old plans to the new format adding the attribute routing mode. -gl nov'19
     */
    @Override
    public String identifyMainMode(List<? extends PlanElement> tripElements) {
        String identifiedMode = null;
        for (PlanElement pe : tripElements) {
            if (pe instanceof Leg) {
                String mode = ((Leg) pe).getMode();
                if (transitModes.contains(mode)) {
                    return mode;
                }
                if (TransportMode.transit_walk.equals(mode)) {
                    identifiedMode = TransportMode.pt;
                }
                if (identifiedMode == null
                        && !TransportMode.walk.equals(mode)) {
                    identifiedMode = mode;
                }
            }
        }

        if (identifiedMode != null) {
            return identifiedMode;
        }
        if (tripElements.size() == 1) {
            Leg l = (Leg) tripElements.get(0);
            return l.getMode();
        }

        throw new RuntimeException("could not identify main mode: " + tripElements);
    }
}
