/* *********************************************************************** *
 * project: org.matsim.* 												   *
 *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2023 by the members listed in the COPYING,        *
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
package ch.sbb.matsim.routing.pt.raptor;

import java.util.function.Supplier;

import ch.sbb.matsim.routing.pt.raptor.SwissRailRaptorCore.PathElement;


/**
 * @author mrieser / Simunto
 */
public class SBBRaptorTransferCostCalculator implements RaptorTransferCostCalculator {
	private static final int TIME_UNDEFINED = -2147483648;

	@Override
	public double calcTransferCost(SwissRailRaptorCore.PathElement currentPE, Supplier<Transfer> transfer, RaptorStaticConfig staticConfig, RaptorParameters raptorParams, int totalTravelTime, int transferCount, double existingTransferCosts, double currentTime) {
		double transferCostBase = raptorParams.getTransferPenaltyFixCostPerTransfer();
		double transferCostPerHour = raptorParams.getTransferPenaltyPerTravelTimeHour();
		double transferCostMin = raptorParams.getTransferPenaltyMinimum();
		double transferCostMax = raptorParams.getTransferPenaltyMaximum();

		if (staticConfig.isUseModeToModeTransferPenalty()) {
			double transferCostModeToMode = staticConfig.getModeToModeTransferPenalty(transfer.get().getFromTransitRoute().getTransportMode(), transfer.get().getToTransitRoute().getTransportMode());
			if (transferCostModeToMode == 0) {
				SwissRailRaptorCore.PathElement firstPEOfTripPart = getFirstPEOfTripPart(currentPE, staticConfig);
				double baseArrivalTransferCost = firstPEOfTripPart.arrivalTransferCost;
				int transferCountSinceModeChange = transferCount - firstPEOfTripPart.transferCount;
				double travelTimeSinceModeChange = totalTravelTime;
				if ((firstPEOfTripPart.comingFrom != null) & (baseArrivalTransferCost != 0)) {
					if (firstPEOfTripPart.isTransfer) {
						travelTimeSinceModeChange = currentTime + transfer.get().getTransferTime() - firstPEOfTripPart.arrivalTime;
					} else {
						travelTimeSinceModeChange = currentTime + transfer.get().getTransferTime() - firstPEOfTripPart.boardingTime;
					}
				}
				double singleTransferCost = calcSingleTransferCost(transferCostBase, transferCostPerHour, transferCostMin, transferCostMax, travelTimeSinceModeChange);
//				double oldCost = (calcSingleTransferCost(transferCostBase, transferCostPerHour, transferCostMin, transferCostMax, totalTravelTime) * transferCount) - existingTransferCosts - baseArrivalTransferCost;
//				double newCost = (singleTransferCost * transferCountSinceModeChange) - existingTransferCosts + baseArrivalTransferCost;
				return (singleTransferCost * transferCountSinceModeChange) - existingTransferCosts + baseArrivalTransferCost;
			} else {
				return existingTransferCosts + transferCostModeToMode;
			}
		} else {
			return (calcSingleTransferCost(transferCostBase, transferCostPerHour, transferCostMin, transferCostMax, totalTravelTime) * transferCount) - existingTransferCosts;
		}
	}

	public PathElement getFirstPEOfTripPart(SwissRailRaptorCore.PathElement fromPE, RaptorStaticConfig staticConfig) {
		PathElement firstElement = fromPE;
		PathElement checkElement = fromPE;
		String mode = checkElement.toRouteStop.route.getTransportMode();
		while ((checkElement.comingFrom != null) && ((staticConfig.isUseModeToModeTransferPenalty() ? staticConfig.getModeToModeTransferPenalty(checkElement.comingFrom.toRouteStop.route.getTransportMode(), mode):0)==0)) {
			checkElement = checkElement.comingFrom;
			if (checkElement.boardingTime != TIME_UNDEFINED) {
				firstElement = checkElement;
			}
		}

		return firstElement;
	}

	private double calcSingleTransferCost(double costBase, double costPerHour, double costMin, double costMax, double travelTime) {
		double cost = costBase + costPerHour / 3600 * travelTime;
		double max = Math.max(costMin, costMax);
		double min = Math.min(costMin, costMax);
		if (cost > max) {
			cost = max;
		}
		if (cost < min) {
			cost = min;
		}
		return cost;
	}

}
