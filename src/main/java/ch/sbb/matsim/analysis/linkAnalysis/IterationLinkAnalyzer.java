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

import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.IdMap;
import org.matsim.api.core.v01.events.LinkEnterEvent;
import org.matsim.api.core.v01.events.VehicleEntersTrafficEvent;
import org.matsim.api.core.v01.events.handler.LinkEnterEventHandler;
import org.matsim.api.core.v01.events.handler.VehicleEntersTrafficEventHandler;
import org.matsim.api.core.v01.network.Link;

/**
 * Counts the vehicles per iteration on all links
 */
public class IterationLinkAnalyzer implements LinkEnterEventHandler, VehicleEntersTrafficEventHandler {

    private final IdMap<Link, Integer> countPerLink = new IdMap<>(Link.class);

    @Override
    public void handleEvent(LinkEnterEvent event) {
        var linkId = event.getLinkId();

        int count = countPerLink.getOrDefault(linkId, 0);
        count++;
        countPerLink.put(linkId, count);

    }

    @Override
    public void handleEvent(VehicleEntersTrafficEvent event) {
        var linkId = event.getLinkId();

        int count = countPerLink.getOrDefault(linkId, 0);
        count++;
        countPerLink.put(linkId, count);
    }

    @Override
    public void reset(int iteration) {
        countPerLink.clear();
    }

    public Map<Id<Link>, Integer> getIterationCounts() {
        return countPerLink.entrySet().stream().collect(Collectors.toMap(Entry::getKey, Entry::getValue, (k, k2) -> k, TreeMap::new));

    }
}
