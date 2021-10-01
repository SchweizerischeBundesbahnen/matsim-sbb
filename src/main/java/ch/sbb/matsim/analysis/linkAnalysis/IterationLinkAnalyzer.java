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

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import javax.inject.Inject;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.events.LinkEnterEvent;
import org.matsim.api.core.v01.events.VehicleEntersTrafficEvent;
import org.matsim.api.core.v01.events.handler.LinkEnterEventHandler;
import org.matsim.api.core.v01.events.handler.VehicleEntersTrafficEventHandler;
import org.matsim.api.core.v01.network.Link;
import org.matsim.core.api.experimental.events.EventsManager;

/**
 * Counts the vehicles per iteration on all links
 */
public class IterationLinkAnalyzer implements LinkEnterEventHandler, VehicleEntersTrafficEventHandler {

    private Map<Id<Link>, AtomicInteger> countPerLink = new HashMap<>();

    @Inject
    public IterationLinkAnalyzer(EventsManager eventsManager) {
        eventsManager.addHandler(this);
    }

    @Override
    public void handleEvent(LinkEnterEvent event) {
        var linkId = event.getLinkId();
        countPerLink.computeIfAbsent(linkId, i -> new AtomicInteger(0)).incrementAndGet();

    }

    @Override
    public void handleEvent(VehicleEntersTrafficEvent event) {
        var linkId = event.getLinkId();
        countPerLink.computeIfAbsent(linkId, i -> new AtomicInteger(0)).incrementAndGet();
    }

    @Override
    public void reset(int iteration) {
        countPerLink.clear();
    }

    public Map<Id<Link>, Integer> getIterationCounts() {
        return countPerLink.entrySet().stream().collect(Collectors.toMap(k -> k.getKey(), k -> k.getValue().intValue(), (k, k2) -> k, TreeMap::new));

    }
}
