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
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import static java.lang.Double.max;


/**
 * @author mrieser / Simunto
 */
public class SBBRaptorTransferCostCalculator implements RaptorTransferCostCalculator {
	private static final int TIME_UNDEFINED =  Integer.MIN_VALUE;
	private final static Logger log = LogManager.getLogger(SBBRaptorTransferCostCalculator.class);

	@Override
	public double calcTransferCost(SwissRailRaptorCore.PathElement currentPE, Supplier<Transfer> transfer, RaptorStaticConfig staticConfig, RaptorParameters raptorParams, int totalTravelTime, int transferCount, double existingTransferCosts, double currentTime) {
		double transferCostBase = raptorParams.getTransferPenaltyFixCostPerTransfer();
		double transferCostPerHour = raptorParams.getTransferPenaltyPerTravelTimeHour();
		double transferCostMin = raptorParams.getTransferPenaltyMinimum();
		double transferCostMax = raptorParams.getTransferPenaltyMaximum();

		if (staticConfig.isUseModeToModeTransferPenalty()) {
			double cost;
			// transferCostModeToMode is only relevant if this is a transfer, not if transferCost is recalculated after a pt trip
			boolean isTransfer = transfer.get().getFromStop().equals(currentPE.toRouteStop.routeStop.getStopFacility());
			double transferCostModeToMode =  isTransfer ? staticConfig.getModeToModeTransferPenalty(transfer.get().getFromTransitRoute().getTransportMode(), transfer.get().getToTransitRoute().getTransportMode()) : 0;

			if (transferCostModeToMode == 0) {
				int transferCountSinceModeChange = 0;
				double travelTimeSinceModeChange = 0;

				SwissRailRaptorCore.PathElement firstPEOfTripPart = getFirstPEOfTripPart(currentPE, staticConfig);
				double baseArrivalTransferCost = firstPEOfTripPart.arrivalTransferCost;

				if (currentPE.firstDepartureTime != TIME_UNDEFINED) {
					currentTime = currentPE.firstDepartureTime + totalTravelTime;
				}
				if (!firstPEOfTripPart.equals(currentPE)) {
					travelTimeSinceModeChange = currentTime + (isTransfer ? transfer.get().getTransferTime() : 0) - max(currentPE.firstDepartureTime, firstPEOfTripPart.vehDepartureTime);
				}

				transferCountSinceModeChange = transferCount - firstPEOfTripPart.transferCount;

				double singleTransferCost = calcSingleTransferCost(transferCostBase, transferCostPerHour, transferCostMin, transferCostMax, travelTimeSinceModeChange);
				cost = baseArrivalTransferCost + (singleTransferCost * transferCountSinceModeChange) - existingTransferCosts;;
			} else{
				double baseArrivalTransferCost = currentPE.arrivalTransferCost;
				cost = baseArrivalTransferCost + transferCostModeToMode - existingTransferCosts;
			}
// 			double oldCost = (calcSingleTransferCost(transferCostBase, transferCostPerHour, transferCostMin, transferCostMax, totalTravelTime) * transferCount) - existingTransferCosts;
//			log.info("new method;" + oldCost + ";" + cost);
//			if (cost == 0) {
//				int jjj = 0;
//			}
			return cost;
		} else {
			return (calcSingleTransferCost(transferCostBase, transferCostPerHour, transferCostMin, transferCostMax, totalTravelTime) * transferCount) - existingTransferCosts;
		}
	}

	public PathElement getFirstPEOfTripPart(SwissRailRaptorCore.PathElement fromPE, RaptorStaticConfig staticConfig) {
		PathElement checkElement = fromPE;
		PathElement elementWithDeparture = fromPE;
		String mode = checkElement.toRouteStop.route.getTransportMode();
		while ((checkElement.comingFrom != null) && ((staticConfig.isUseModeToModeTransferPenalty()& checkElement.isTransfer) ? staticConfig.getModeToModeTransferPenalty(checkElement.comingFrom.toRouteStop.route.getTransportMode(), mode):0)==0) {
			mode = checkElement.toRouteStop.route.getTransportMode();
			checkElement = checkElement.comingFrom;
			if (checkElement.vehDepartureTime != TIME_UNDEFINED) {
				elementWithDeparture = checkElement;
			}
		}

		return elementWithDeparture;
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
