package ch.sbb.matsim.replanning;

import ch.sbb.matsim.config.variables.SBBModes;
import ch.sbb.matsim.config.variables.Variables;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import jakarta.inject.Inject;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.core.config.Config;
import org.matsim.core.population.algorithms.PermissibleModesCalculator;

public class SBBPermissibleModesCalculator implements PermissibleModesCalculator {

	private final List<String> availableModes;
		private final List<String> availableModesWithoutCar;
		private final boolean considerCarAvailability;

		@Inject
		public SBBPermissibleModesCalculator(Config config) {
			this.availableModes = Arrays.asList(config.subtourModeChoice().getModes());

			if (this.availableModes.contains(SBBModes.CAR)) {
				final List<String> l = new ArrayList<>(this.availableModes);
				while (l.remove(SBBModes.CAR)) {
				}
				this.availableModesWithoutCar = Collections.unmodifiableList(l);
			} else {
				this.availableModesWithoutCar = this.availableModes;
			}

			this.considerCarAvailability = config.subtourModeChoice().considerCarAvailability();
		}

		@Override
		public Collection<String> getPermissibleModes(final Plan plan) {
			if (!considerCarAvailability) return availableModes;
			final Person person;
			try {
				person = plan.getPerson();
			}
			catch (ClassCastException e) {
				throw new IllegalArgumentException( "I need a PersonImpl to get car availability" );
			}
			final boolean carAvail = person.getAttributes().getAttribute(Variables.CAR_AVAIL).toString().equals(Variables.CAR_AVAL_TRUE);
			return carAvail ? availableModes : availableModesWithoutCar;
		}


}
