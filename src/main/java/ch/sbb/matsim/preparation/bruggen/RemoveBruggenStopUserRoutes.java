/* *********************************************************************** *
 * project: org.matsim.*
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2020 by the members listed in the COPYING,        *
 *                   LICENSE and WARRANTY file.                            *
 * email           : info at matsim dot org                                *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *   See also COPYING, LICENSE and WARRANTY file                           *
 *                                                                         *
 * *********************************************************************** */

package ch.sbb.matsim.preparation.bruggen;

import ch.sbb.matsim.config.variables.SBBModes;
import ch.sbb.matsim.utils.RemoveAgentRoutes.SBBTripsToLegsAlgorithm;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.io.StreamingPopulationReader;
import org.matsim.core.population.io.StreamingPopulationWriter;
import org.matsim.core.router.RoutingModeMainModeIdentifier;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.pt.routes.TransitPassengerRoute;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;

public class RemoveBruggenStopUserRoutes {

    public static void main(String[] args) {
        RoutingModeMainModeIdentifier m = new RoutingModeMainModeIdentifier();
        SBBTripsToLegsAlgorithm algorithm = new SBBTripsToLegsAlgorithm(m, Set.of(SBBModes.PT));
        final Set<Id<TransitStopFacility>> bruggen = Set
                .of(Id.create("2753", TransitStopFacility.class), Id.create("485895731", TransitStopFacility.class), Id.create("485895991", TransitStopFacility.class));
        StreamingPopulationReader streamingPopulationReader = new StreamingPopulationReader(ScenarioUtils.createScenario(ConfigUtils.createConfig()));
        StreamingPopulationWriter streamingPopulationWriter = new StreamingPopulationWriter();
        streamingPopulationWriter.startStreaming(args[1]);
        AtomicInteger i = new AtomicInteger();
        streamingPopulationReader.addAlgorithm(person -> {
            for (Plan plan : person.getPlans()) {
                boolean reroutePt = TripStructureUtils.getLegs(plan)
                        .stream()
                        .filter(leg -> leg.getMode().equals(SBBModes.PT))
                        .anyMatch(leg -> {
                            TransitPassengerRoute r = (TransitPassengerRoute) leg.getRoute();
                            return (bruggen.contains(r.getAccessStopId()) || bruggen.contains(r.getEgressStopId()));
                        });
                if (reroutePt) {
                    algorithm.run(plan);
                    i.incrementAndGet();
                }
            }

            streamingPopulationWriter.run(person);
        });

        streamingPopulationReader.readFile(args[0]);
        streamingPopulationWriter.closeStreaming();
        System.out.println(i.get());
    }

}
