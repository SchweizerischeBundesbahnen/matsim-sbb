package ch.sbb.matsim.routing.network;

import ch.sbb.matsim.config.variables.SBBActivities;
import ch.sbb.matsim.config.variables.SBBModes;
import java.util.HashSet;
import java.util.Set;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.AbstractModule;

/**
 * @author jbischoff / SBB
 */
public class SBBNetworkRoutingModule extends AbstractModule {

	public static void addNetworkMode(Network network, String transportMode, String routingMode) {
		for (Link l : network.getLinks().values()) {
			if (l.getAllowedModes().contains(routingMode)) {
				Set<String> allowedModes = new HashSet<>(l.getAllowedModes());
				allowedModes.add(transportMode);
				l.setAllowedModes(allowedModes);
			}
		}
	}

	public static void prepareScenario(Scenario scenario) {
		Set<String> routedModes = ConfigUtils.addOrGetModule(scenario.getConfig(), SBBNetworkRoutingConfigGroup.class).getNetworkRoutingModes();
		for (String mode : routedModes) {
			addNetworkMode(scenario.getNetwork(), mode, SBBModes.CAR);
			SBBActivities.stageActivityTypeList.add(mode + " interaction");
			Set<String> networkModes = new HashSet<>(scenario.getConfig().plansCalcRoute().getNetworkModes());
			networkModes.add(mode);
			scenario.getConfig().plansCalcRoute().setNetworkModes(networkModes);
		}
	}

	@Override
	public void install() {
		Set<String> routedModes = ConfigUtils.addOrGetModule(getConfig(), SBBNetworkRoutingConfigGroup.class).getNetworkRoutingModes();
		for (String mode : routedModes) {
			addTravelTimeBinding(mode).to(networkTravelTime());
			addTravelDisutilityFactoryBinding(mode).to(carTravelDisutilityFactoryKey());
		}
	}
}

