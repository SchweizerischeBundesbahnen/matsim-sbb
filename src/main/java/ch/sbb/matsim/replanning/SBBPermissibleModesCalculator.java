package ch.sbb.matsim.replanning;

import ch.sbb.matsim.config.SBBReplanningConfigGroup;
import ch.sbb.matsim.config.variables.SBBModes;
import ch.sbb.matsim.config.variables.Variables;
import jakarta.inject.Inject;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.algorithms.PermissibleModesCalculator;

import java.util.*;

public class SBBPermissibleModesCalculator implements PermissibleModesCalculator {

	private final List<String> availableModes;
		private final List<String> availableModesWithoutCar;
	private final SBBReplanningConfigGroup.CarModeAllowedSetting carModeAllowedSetting;
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

			this.carModeAllowedSetting = ConfigUtils.addOrGetModule(config, SBBReplanningConfigGroup.class).getCarModeAllowedSetting();
		}

		@Override
		public Collection<String> getPermissibleModes(final Plan plan) {
			final Person person;
			try {
				person = plan.getPerson();
			}
			catch (ClassCastException e) {
				throw new IllegalArgumentException( "I need a PersonImpl to get car availability" );
			}

			final boolean carAvailToAgent = switch (carModeAllowedSetting) {
				case always -> true;
				case carAvailable ->
						Objects.equals(String.valueOf(person.getAttributes().getAttribute(Variables.CAR_AVAIL)), Variables.CAR_AVAL_TRUE);
				case licenseAvailable ->
						Objects.equals(String.valueOf(person.getAttributes().getAttribute(Variables.HAS_DRIVING_LICENSE)), Variables.CAR_AVAL_TRUE);
			};

			return carAvailToAgent ? availableModes : availableModesWithoutCar;
		}


}
