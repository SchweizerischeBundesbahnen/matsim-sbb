/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2017.
 */

package ch.sbb.matsim.analysis;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.events.LinkLeaveEvent;
import org.matsim.api.core.v01.events.VehicleEntersTrafficEvent;
import org.matsim.api.core.v01.events.handler.LinkLeaveEventHandler;
import org.matsim.api.core.v01.events.handler.VehicleEntersTrafficEventHandler;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.vehicles.Vehicle;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Counts the number of vehicles leaving a link, aggregated into time bins of a specified size.
 *
 * @author mrieser, pmanser
 */
public class VolumesAnalyzerSBB implements LinkLeaveEventHandler, VehicleEntersTrafficEventHandler {

	private final static Logger log = Logger.getLogger(VolumesAnalyzerSBB.class);
	private final int timeBinSize;
	private final int maxTime;
	private final int maxSlotIndex;
	private final Map<Id<Link>, int[]> linksNbVehicles;
	// for multi-modal support
	private final boolean observeModes;
	private final Map<Id<Vehicle>, String> enRouteModes;
	private final Map<Id<Link>, Map<String, int[]>> linksNbVehiclesPerMode;
	private Set<Id<Link>> linkFilter = null;

	public VolumesAnalyzerSBB(final int timeBinSize, final int maxTime, final Network network) {
		this(timeBinSize, maxTime, network, true);
	}

	public VolumesAnalyzerSBB(final int timeBinSize, final int maxTime, final Network network, boolean observeModes) {
		this.timeBinSize = timeBinSize;
		this.maxTime = maxTime;
		this.maxSlotIndex = (this.maxTime / this.timeBinSize) + 1;
		this.linksNbVehicles = new HashMap<>((int) (network.getLinks().size() * 1.1), 0.95f);

		this.observeModes = observeModes;
		if (this.observeModes) {
			this.enRouteModes = new HashMap<>();
			this.linksNbVehiclesPerMode = new HashMap<>((int) (network.getLinks().size() * 1.1), 0.95f);
		} else {
			this.enRouteModes = null;
			this.linksNbVehiclesPerMode = null;
		}
	}

	public void setLinkFilter(Set<Id<Link>> linkFilter) {
		this.linkFilter = linkFilter;
	}

	private boolean useLink(Id<Link> linkId) {
		if (this.linkFilter == null) {
			return true;
		}
		return this.linkFilter.contains(linkId);
	}

	@Override
	public void handleEvent(VehicleEntersTrafficEvent event) {
		//(PM) in our case, this does not help anything. All networkmodes are defined as 'car' in the event-file
		if (observeModes) {
			enRouteModes.put(event.getVehicleId(), event.getNetworkMode());
		}
	}

	@Override
	public void handleEvent(final LinkLeaveEvent event) {
		if (!useLink(event.getLinkId())) {
			return;
		}
		int timeslot = getTimeSlotIndex(event.getTime());

		int[] nbVehicles = this.linksNbVehicles.computeIfAbsent(event.getLinkId(), k -> new int[this.maxSlotIndex + 1]);
		// initialized to 0 by default, according to JVM specs
		nbVehicles[timeslot]++;

		if (observeModes) {
			Map<String, int[]> modeNbVehicles = this.linksNbVehiclesPerMode.computeIfAbsent(event.getLinkId(), k -> new HashMap<>());

			String mode = enRouteModes.get(event.getVehicleId());
			nbVehicles = modeNbVehicles.computeIfAbsent(mode, k -> new int[this.maxSlotIndex + 1]);
			// initialized to 0 by default, according to JVM specs
			nbVehicles[timeslot]++;
		}
	}

	private int getTimeSlotIndex(final double time) {
		if (time > this.maxTime) {
			return this.maxSlotIndex;
		}
		return ((int) time / this.timeBinSize);
	}

	/**
	 * @param linkId
	 * @return Array containing the number of vehicles leaving the link <code>linkId</code> per time bin, starting with time bin 0 from 0 seconds to (timeBinSize-1)seconds.
	 */
	public int[] getVolumesForLink(final Id<Link> linkId) {
		return this.linksNbVehicles.get(linkId);
	}

	/**
	 * @param linkId
	 * @param mode
	 * @return Array containing the number of vehicles using the specified mode leaving the link
	 * 		<code>linkId</code> per time bin, starting with time bin 0 from 0 seconds to (timeBinSize-1)seconds.
	 */
	public int[] getVolumesForLink(final Id<Link> linkId, String mode) {
		if (observeModes) {
			Map<String, int[]> modeVolumes = this.linksNbVehiclesPerMode.get(linkId);
			if (modeVolumes != null) {
				return modeVolumes.get(mode);
			}
		}
		return null;
	}

	/**
	 * @return The size of the arrays returned by calls to the {@link #getVolumesForLink(Id)} and the {@link #getVolumesForLink(Id, String)} methods.
	 */
	public int getVolumesArraySize() {
		return this.maxSlotIndex + 1;
	}

	/**
	 * @return Set of Strings containing all modes for which counting-values are available.
	 */
	public Set<String> getModes() {
		Set<String> modes = new TreeSet<>();

		for (Map<String, int[]> map : this.linksNbVehiclesPerMode.values()) {
			modes.addAll(map.keySet());
		}

		return modes;
	}

	/**
	 * @return Set of Strings containing all link ids for which counting-values are available.
	 */
	public Set<Id<Link>> getLinkIds() {
		return this.linksNbVehicles.keySet();
	}

	@Override
	public void reset(final int iteration) {
		this.linksNbVehicles.clear();
		if (observeModes) {
			this.linksNbVehiclesPerMode.clear();
			this.enRouteModes.clear();
		}
	}

}
