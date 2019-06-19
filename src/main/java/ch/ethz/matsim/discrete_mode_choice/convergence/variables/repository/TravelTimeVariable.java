package ch.ethz.matsim.discrete_mode_choice.convergence.variables.repository;

import ch.ethz.matsim.discrete_mode_choice.convergence.variables.DistributionVariable;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.events.PersonArrivalEvent;
import org.matsim.api.core.v01.events.PersonDepartureEvent;
import org.matsim.api.core.v01.events.handler.PersonArrivalEventHandler;
import org.matsim.api.core.v01.events.handler.PersonDepartureEventHandler;
import org.matsim.api.core.v01.population.Person;

import java.util.*;

public class TravelTimeVariable
		implements DistributionVariable, PersonDepartureEventHandler, PersonArrivalEventHandler {
	private final List<Double> currentTravelTimes = new ArrayList<>();
	private final List<Double> stateTravelTimes = new ArrayList<>();
	private final Map<Id<Person>, PersonDepartureEvent> departureEvents = new HashMap<>();

	private final String mode;
	private int lastIteration = -1;

	public TravelTimeVariable(String mode) {
		this.mode = mode;
	}

	@Override
	public void handleEvent(PersonDepartureEvent departureEvent) {
		if (departureEvent.getLegMode().equals(mode)) {
			departureEvents.put(departureEvent.getPersonId(), departureEvent);
		}
	}

	@Override
	public void handleEvent(PersonArrivalEvent arrivalEvent) {
		PersonDepartureEvent departureEvent = departureEvents.remove(arrivalEvent.getPersonId());

		if (departureEvent != null) {
			double travelTime = arrivalEvent.getTime() - departureEvent.getTime();
			currentTravelTimes.add(travelTime);
		}
	}

	@Override
	public void update(int iteration) {
		if (lastIteration < iteration) {
			stateTravelTimes.addAll(currentTravelTimes);
			currentTravelTimes.clear();
			lastIteration = iteration;
		}
	}

	@Override
	public Collection<Double> getValues() {
		return Collections.unmodifiableCollection(stateTravelTimes);
	}

	@Override
	public String getName() {
		return "travel_time_" + mode;
	}

}
