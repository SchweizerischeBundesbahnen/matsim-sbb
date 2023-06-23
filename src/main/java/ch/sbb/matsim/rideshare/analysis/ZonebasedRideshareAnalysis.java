package ch.sbb.matsim.rideshare.analysis;

import ch.sbb.matsim.config.PostProcessingConfigGroup;
import ch.sbb.matsim.config.variables.Variables;
import ch.sbb.matsim.csv.CSVWriter;
import ch.sbb.matsim.zones.Zone;
import ch.sbb.matsim.zones.Zones;
import ch.sbb.matsim.zones.ZonesCollection;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.events.PersonArrivalEvent;
import org.matsim.api.core.v01.events.PersonDepartureEvent;
import org.matsim.api.core.v01.events.PersonEntersVehicleEvent;
import org.matsim.api.core.v01.events.handler.PersonArrivalEventHandler;
import org.matsim.api.core.v01.events.handler.PersonDepartureEventHandler;
import org.matsim.api.core.v01.events.handler.PersonEntersVehicleEventHandler;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.Person;
import org.matsim.contrib.drt.passenger.events.DrtRequestSubmittedEvent;
import org.matsim.contrib.drt.passenger.events.DrtRequestSubmittedEventHandler;
import org.matsim.contrib.drt.run.MultiModeDrtConfigGroup;
import org.matsim.contrib.dvrp.optimizer.Request;
import org.matsim.contrib.dvrp.passenger.PassengerRequestRejectedEvent;
import org.matsim.contrib.dvrp.passenger.PassengerRequestRejectedEventHandler;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;

import jakarta.inject.Inject;

import java.io.IOException;
import java.util.DoubleSummaryStatistics;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author jbischoff / SBB
 */
public class ZonebasedRideshareAnalysis implements DrtRequestSubmittedEventHandler, PersonDepartureEventHandler, PersonArrivalEventHandler, PersonEntersVehicleEventHandler,
		PassengerRequestRejectedEventHandler {

	private final Zones zones;
	private final Set<String> modes;
	private final Map<Id<Person>, PendingDRTTrip> currentDepartures = new HashMap<>();
	private final Map<Id<Link>, Id<Zone>> links2zoneCache = new HashMap<>();
	private final Map<Id<Request>, Id<Person>> openRequests = new HashMap<>();
	private final Network network;
	private Map<String, Map<Id<Zone>, DoubleSummaryStatistics>> detourStats = new HashMap<>();
	private Map<String, Map<Id<Zone>, DoubleSummaryStatistics>> waitStats = new HashMap<>();

	@Inject
	public ZonebasedRideshareAnalysis(ZonesCollection zonesCollection, Config config, Network network, EventsManager eventsManager) {
		this.network = network;
		this.zones = zonesCollection.getZones(ConfigUtils.addOrGetModule(config, PostProcessingConfigGroup.class).getZonesId());
		modes = MultiModeDrtConfigGroup.get(config).modes().collect(Collectors.toSet());
		eventsManager.addHandler(this);
	}

	public void writeStats(String drtZoneStats) {
		for (String mode : modes) {
			String zone = "zoneId";
			String waitAverage = mode + "_MeanWaitTime";
			String detourAverage = mode + "_MeanDetour";
			String count = mode + "_Count";
			Map<Id<Zone>, DoubleSummaryStatistics> modeDetourStats = detourStats.get(mode);
			Map<Id<Zone>, DoubleSummaryStatistics> modeWaitStats = waitStats.get(mode);
			try (CSVWriter writer = new CSVWriter(null, new String[]{zone, waitAverage, detourAverage, count}, drtZoneStats + "_" + mode + ".csv")) {
				for (Map.Entry<Id<Zone>, DoubleSummaryStatistics> entry : modeWaitStats.entrySet()) {
					writer.set(zone, entry.getKey().toString());
					writer.set(waitAverage, Double.toString(entry.getValue().getAverage()));
					writer.set(count, Double.toString(entry.getValue().getCount()));
					writer.set(detourAverage, Double.toString(modeDetourStats.getOrDefault(entry.getKey(), new DoubleSummaryStatistics()).getAverage()));
					writer.writeRow();
				}
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}
	}

	@Override
	public void handleEvent(DrtRequestSubmittedEvent drtRequestSubmittedEvent) {
		openRequests.put(drtRequestSubmittedEvent.getRequestId(), drtRequestSubmittedEvent.getPersonId());
		currentDepartures.get(drtRequestSubmittedEvent.getPersonId()).directTraveltime = drtRequestSubmittedEvent.getUnsharedRideTime();
	}

	@Override
	public void handleEvent(PassengerRequestRejectedEvent passengerRequestRejectedEvent) {

		Id<Person> personId = openRequests.remove(passengerRequestRejectedEvent.getRequestId());
		currentDepartures.remove(personId);

	}

	@Override
	public void handleEvent(PersonDepartureEvent event) {

		if (modes.contains(event.getLegMode())) {
			Id<Zone> startZone = findZone(event.getLinkId());
			PendingDRTTrip departure = new PendingDRTTrip(event.getPersonId(), event.getLegMode(), event.getTime(), startZone);
			currentDepartures.put(event.getPersonId(), departure);
		}
	}

	@Override
	public void handleEvent(PersonArrivalEvent event) {

		PendingDRTTrip trip = currentDepartures.remove(event.getPersonId());
		if (trip != null) {
			trip.arrivalTime = event.getTime();
			finishTrip(trip);
		}
	}

	private void finishTrip(PendingDRTTrip trip) {
		double travelTime = trip.arrivalTime - trip.boardingTime;
		double waitTime = trip.boardingTime - trip.departureTime;
		double detour = Math.max(travelTime / trip.directTraveltime, 1.0);
		waitStats.get(trip.mode).computeIfAbsent(trip.startZone, n -> new DoubleSummaryStatistics()).accept(waitTime);
		detourStats.get(trip.mode).computeIfAbsent(trip.startZone, n -> new DoubleSummaryStatistics()).accept(detour);
	}

	private Id<Zone> findZone(Id<Link> linkId) {
		return links2zoneCache.computeIfAbsent(linkId, l -> {
			Zone zone = zones.findZone(network.getLinks().get(linkId).getCoord());
			return zone == null ? Id.create(Variables.DEFAULT_OUTSIDE_ZONE, Zone.class) : zone.getId();
		});

	}

	@Override
	public void handleEvent(PersonEntersVehicleEvent event) {

		PendingDRTTrip trip = currentDepartures.get(event.getPersonId());
		if (trip != null) {
			trip.boardingTime = event.getTime();
		}
	}

	@Override
	public void reset(int iteration) {
		currentDepartures.clear();
		detourStats = modes.stream().collect(Collectors.toMap(mode -> mode, o -> new HashMap<>()));
		waitStats = modes.stream().collect(Collectors.toMap(mode -> mode, o -> new HashMap<>()));
	}

	private static class PendingDRTTrip {

        private final String mode;
        private final double departureTime;
		private final Id<Zone> startZone;
		private double directTraveltime;
		private double boardingTime;
		private double arrivalTime;

		private PendingDRTTrip(Id<Person> personId, String mode, double departureTime, Id<Zone> startZone) {
            this.mode = mode;
            this.departureTime = departureTime;
			this.startZone = startZone;

		}
	}

}
