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

package ch.sbb.matsim.analysis.tripsandlegsanalysis;

import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.matsim.analysis.TripsAndLegsCSVWriter.CustomTripsWriterExtension;
import org.matsim.core.router.TripStructureUtils.Trip;

@Singleton
public class SBBTripsExtension implements CustomTripsWriterExtension {

    private final RailTripsAnalyzer railTripsAnalyzer;

    @Inject
    public SBBTripsExtension(RailTripsAnalyzer railTripsAnalyzer) {
        this.railTripsAnalyzer = railTripsAnalyzer;
    }

    @Override
    public String[] getAdditionalTripHeader() {
        return new String[]{"rail_pkm"};
    }

    @Override
    public List<String> getAdditionalTripColumns(Trip trip) {
        String rail_pkm = calcRailPkm(trip);
        return List.of(rail_pkm);
    }

    private String calcRailPkm(Trip trip) {
        var rail_pm = railTripsAnalyzer.calcRailDistance(trip);
        return String.valueOf(rail_pm / 1000);
    }
}
