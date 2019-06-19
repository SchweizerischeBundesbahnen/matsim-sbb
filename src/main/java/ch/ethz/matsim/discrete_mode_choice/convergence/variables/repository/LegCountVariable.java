package ch.ethz.matsim.discrete_mode_choice.convergence.variables.repository;

import ch.ethz.matsim.discrete_mode_choice.convergence.variables.CategoricalVariable;
import org.matsim.api.core.v01.events.PersonDepartureEvent;
import org.matsim.api.core.v01.events.handler.PersonDepartureEventHandler;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class LegCountVariable implements CategoricalVariable, PersonDepartureEventHandler {
	private final Collection<String> modes;

	private final Map<String, Double> currentValues = new HashMap<>();
	private final Map<String, Double> stateValues = new HashMap<>();
	private int lastIteration = -1;

	public LegCountVariable(Collection<String> modes) {
		this.modes = modes;

		modes.forEach(m -> currentValues.put(m, 0.0));
		modes.forEach(m -> stateValues.put(m, Double.NaN));
	}

	@Override
	public void update(int iteration) {
		if (iteration > lastIteration) {
			stateValues.putAll(currentValues);
			modes.forEach(m -> currentValues.put(m, 0.0));
			lastIteration = iteration;
		}
	}

	@Override
	public void handleEvent(PersonDepartureEvent event) {
		if (modes.contains(event.getLegMode())) {
			currentValues.put(event.getLegMode(), currentValues.get(event.getLegMode()) + 1);
		}
	}

	@Override
	public Map<String, Double> getValues() {
		return Collections.unmodifiableMap(stateValues);
	}

	@Override
	public String getName() {
		return "leg_count";
	}
}
