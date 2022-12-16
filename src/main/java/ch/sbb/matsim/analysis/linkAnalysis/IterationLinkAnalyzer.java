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

package ch.sbb.matsim.analysis.linkAnalysis;

import ch.sbb.matsim.config.variables.Variables;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.stream.Collectors;
import javax.inject.Inject;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.IdMap;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.events.LinkEnterEvent;
import org.matsim.api.core.v01.events.VehicleEntersTrafficEvent;
import org.matsim.api.core.v01.events.handler.LinkEnterEventHandler;
import org.matsim.api.core.v01.events.handler.VehicleEntersTrafficEventHandler;
import org.matsim.api.core.v01.network.Link;
import org.matsim.vehicles.Vehicle;

/**
 * Counts the vehicles per iteration on all links
 */
public class IterationLinkAnalyzer implements LinkEnterEventHandler, VehicleEntersTrafficEventHandler {

    @Inject
    private Scenario scenario;

    enum VehicleType {freight, car}
    private final IdMap<Link, LinkStorage> countPerLink = new IdMap<>(Link.class);
    private Map<Id<Vehicle>, VehicleType> identification = new HashMap<>();

    public IterationLinkAnalyzer() {
    }

    public IterationLinkAnalyzer(Scenario scenario) {
        this.scenario = scenario;
    }

    @Override
    public void handleEvent(LinkEnterEvent event) {
        var linkId = event.getLinkId();
        var linkStorage = countPerLink.getOrDefault(linkId, new LinkStorage(linkId));
        var vehicleType = identification.get(event.getVehicleId());
        // check if the vehicle schould be counted
        if (vehicleType != null) {
            linkStorage.increase(vehicleType);
            countPerLink.put(linkId, linkStorage);
        }
    }

    @Override
    public void handleEvent(VehicleEntersTrafficEvent event) {
        var linkId = event.getLinkId();

        // Skip all other pt vehicles
        if (event.getPersonId().toString().contains("pt")) {
            return;
        }

        var linkStorage = countPerLink.getOrDefault(linkId, new LinkStorage(linkId));

        // Check if the vehicle is a Lkw
        if (scenario.getPopulation().getPersons().get(event.getPersonId()).getAttributes().getAttribute("subpopulation").toString().contains(Variables.FREIGHT_ROAD)) {
            linkStorage.increase(VehicleType.freight);
            countPerLink.put(linkId, linkStorage);
            identification.put(event.getVehicleId(), VehicleType.freight);
        } else {
            linkStorage.increase(VehicleType.car);
            countPerLink.put(linkId, linkStorage);
            identification.put(event.getVehicleId(), VehicleType.car);
        }
    }

    @Override
    public void reset(int iteration) {
        countPerLink.clear();
    }

    public Map<Id<Link>, LinkStorage> getIterationCounts() {
        return countPerLink.entrySet().stream().collect(Collectors.toMap(Entry::getKey, Entry::getValue, (k, k2) -> k, TreeMap::new));

    }
}
