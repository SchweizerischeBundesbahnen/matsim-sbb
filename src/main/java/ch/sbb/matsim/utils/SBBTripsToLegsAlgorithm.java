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

package ch.sbb.matsim.utils;

import java.util.List;
import java.util.Set;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.population.algorithms.PlanAlgorithm;
import org.matsim.core.router.MainModeIdentifier;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.core.router.TripStructureUtils.Trip;

public class SBBTripsToLegsAlgorithm implements PlanAlgorithm {

	private final MainModeIdentifier mainModeIdentifier;
	private final Set<String> modesToClear;

	public SBBTripsToLegsAlgorithm(final MainModeIdentifier mainModeIdentifier, Set<String> modesToClear) {
		this.mainModeIdentifier = mainModeIdentifier;
		this.modesToClear = modesToClear;
	}

	@Override
	public void run(final Plan plan) {
		final List<PlanElement> planElements = plan.getPlanElements();
		final List<Trip> trips = TripStructureUtils.getTrips(plan);

		for (Trip trip : trips) {
			final List<PlanElement> fullTrip =
					planElements.subList(
							planElements.indexOf(trip.getOriginActivity()) + 1,
							planElements.indexOf(trip.getDestinationActivity()));
			final String mode = mainModeIdentifier.identifyMainMode(fullTrip);
			if (modesToClear.contains(mode)) {
				fullTrip.clear();
				fullTrip.add(PopulationUtils.createLeg(mode));
				if (fullTrip.size() != 1) {
					throw new RuntimeException(fullTrip.toString());
				}
			}
		}
	}
}
