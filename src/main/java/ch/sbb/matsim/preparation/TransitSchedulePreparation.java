package ch.sbb.matsim.preparation;

import ch.sbb.matsim.config.SBBSupplyConfigGroup;
import ch.sbb.matsim.config.variables.SBBModes.PTSubModes;
import java.net.URL;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.jboss.logging.Logger;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.io.MatsimNetworkReader;

public class TransitSchedulePreparation {

	public static void mergeTransitNetworkFromSupplyConfig(Scenario scenario) {
		SBBSupplyConfigGroup sbbSupplyConfigGroup = ConfigUtils.addOrGetModule(scenario.getConfig(), SBBSupplyConfigGroup.class);
		if (sbbSupplyConfigGroup.getTransitNetworkFileString() != null) {
			URL transitNetworkFile = sbbSupplyConfigGroup.getTransitNetworkFile(scenario.getConfig().getContext());
			if (sbbSupplyConfigGroup.isCheckIfTransitNetworkExistsAlready()) {
				boolean hasPt = scenario.getNetwork().getLinks().values().stream().anyMatch(l -> PTSubModes.submodes.stream().anyMatch(m -> l.getAllowedModes().contains(m)));
				if (hasPt) {
					throw new RuntimeException("Street Network already contains pt links. Use streets only network or overwrite setting in config");
				}
			}
            // This call does not replace the original network, but additively reads the second.
			new MatsimNetworkReader(scenario.getNetwork()).readURL(transitNetworkFile);

		}
	}

	public static void setRoutedModesToDifferentModes(Scenario scenario) {
		Map<String, String> modeMapping = Map.of(
				PTSubModes.RAIL, PTSubModes.RAIL + "_route",
				PTSubModes.BUS, PTSubModes.BUS + "_route",
				PTSubModes.OTHER, PTSubModes.OTHER + "_route",
				PTSubModes.TRAM, PTSubModes.TRAM + "_route"
		);
		Logger.getLogger(TransitSchedulePreparation.class).info("Setting routed modes to different modes for all links and transit routes.");

		scenario.getNetwork().getLinks().values().forEach(l -> {
			Set<String> currentModes = l.getAllowedModes();
			Set<String> newModes = new HashSet<>();
			boolean needsUpdate = false;

			// Iterate exactly once over the link's current modes
			for (String mode : currentModes) {
				if (modeMapping.containsKey(mode)) {
					newModes.add(modeMapping.get(mode));
					needsUpdate = true;
				} else {
					newModes.add(mode);
				}
			}

			// Only overwrite if we actually made a change
			if (needsUpdate) {
				l.setAllowedModes(newModes);
			}
		});

		scenario.getTransitSchedule().getTransitLines().values()
				.stream()
				.flatMap(transitLine -> transitLine.getRoutes().values().stream())
				.forEach(transitRoute -> {
			String mode = transitRoute.getTransportMode();
			if (modeMapping.containsKey(mode)) {
				transitRoute.setTransportMode(modeMapping.get(mode));
			}
		});
	}
}
