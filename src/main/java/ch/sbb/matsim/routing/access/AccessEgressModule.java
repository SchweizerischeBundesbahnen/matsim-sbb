package ch.sbb.matsim.routing.access;

import ch.sbb.matsim.config.SBBAccessTimeConfigGroup;
import ch.sbb.matsim.config.SBBIntermodalConfiggroup;
import ch.sbb.matsim.config.variables.SBBModes;
import ch.sbb.matsim.routing.network.SBBNetworkRoutingConfigGroup;
import ch.sbb.matsim.zones.Zone;
import ch.sbb.matsim.zones.Zones;
import ch.sbb.matsim.zones.ZonesCollection;
import ch.sbb.matsim.zones.ZonesModule;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.contrib.parking.parkingcost.config.ParkingCostConfigGroup;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.PlansCalcRouteConfigGroup.AccessEgressType;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.router.NetworkRoutingProvider;

import java.util.*;
import java.util.stream.Collectors;

public class AccessEgressModule extends AbstractModule {

	public static final String IS_CH = "isCH";

	public static void prepareLinkAttributes(Scenario scenario, boolean includeParkingCosts) {
		ZonesCollection collection = (ZonesCollection) scenario.getScenarioElement(ZonesModule.SBB_ZONES);
		var accessTimeConfigGroup = ConfigUtils.addOrGetModule(scenario.getConfig(), SBBAccessTimeConfigGroup.GROUP_NAME, SBBAccessTimeConfigGroup.class);
		var parkingCostConfigGroup = ConfigUtils.addOrGetModule(scenario.getConfig(), ParkingCostConfigGroup.class);
		var sbbIntermodalConfigGroup = ConfigUtils.addOrGetModule(scenario.getConfig(), SBBIntermodalConfiggroup.class);

		Id<Zones> zonesId = accessTimeConfigGroup.getZonesId();
		Zones zones = collection.getZones(zonesId);
		String attributePrefix = accessTimeConfigGroup.getAttributePrefix();
		Map<String, String> accessTimeParameters = new HashMap<>();
		accessTimeConfigGroup.getModesWithAccessTime().forEach(m -> accessTimeParameters.put(m, attributePrefix + m.toLowerCase()));
		sbbIntermodalConfigGroup.getModeParameterSets().stream()
				.filter(sbbIntermodalModeParameterSet -> sbbIntermodalModeParameterSet.isRoutedOnNetwork() && sbbIntermodalModeParameterSet.getAccessTimeZoneId() != null)
				.forEach(sbbIntermodalModeParameterSet -> accessTimeParameters.put(sbbIntermodalModeParameterSet.getMode(), sbbIntermodalModeParameterSet.getAccessTimeZoneId()));
		Set<String> detourParams = sbbIntermodalConfigGroup.getModeParameterSets().stream()
				.map(sbbIntermodalModeParameterSet -> sbbIntermodalModeParameterSet.getDetourFactorZoneId())
				.filter(Objects::nonNull)
				.collect(Collectors.toSet());

		Map<String, String> parkingCostParameters = new HashMap<>();
		if (includeParkingCosts) {
			parkingCostConfigGroup.getModesWithParkingCosts()
					.forEach(mode -> parkingCostParameters.put(mode, parkingCostConfigGroup.linkAttributePrefix + mode));
		}

		for (var l : scenario.getNetwork().getLinks().values()) {
			Zone zone = zones.findZone(l.getCoord());

			for (var entry : accessTimeParameters.entrySet()) {
				String mode = entry.getKey();
				String attribute = entry.getValue();
				double accessTime = zone != null ? ((Number) zone.getAttribute(attribute)).intValue() : .0;
				if (l.getAllowedModes().contains(mode)) {
					NetworkUtils.setLinkAccessTime(l, mode, accessTime);
					NetworkUtils.setLinkEgressTime(l, mode, accessTime);
				}
			}
			boolean isInCH = false;
			if (zone != null) {
				if (isSwissZone(zone.getId())) {
					isInCH = true;
				}
				for (String detourAttribute : detourParams) {
					double detourFactor = ((Number) zone.getAttribute(detourAttribute)).doubleValue();
					l.getAttributes().putAttribute(detourAttribute, detourFactor);
				}

				for (var entry : parkingCostParameters.entrySet()) {
					String mode = entry.getKey();
					String attribute = entry.getValue();
					double pc = ((Number) zone.getAttribute(attribute)).doubleValue();
					if (l.getAllowedModes().contains(mode)) {
						l.getAttributes().putAttribute(attribute, pc);
					}
				}
			}
			NetworkUtils.setLinkAccessTime(l, SBBModes.BIKE, 1.0);
			NetworkUtils.setLinkEgressTime(l, SBBModes.BIKE, 1.0);
			l.getAttributes().putAttribute(IS_CH, isInCH);
		}

	}

	public static boolean isSwissZone(Id<Zone> id) {
		return (Integer.parseInt(id.toString()) < 700000000);
	}


	@Override
	public void install() {
		Config config = getConfig();
		SBBAccessTimeConfigGroup accessTimeConfigGroup = ConfigUtils.addOrGetModule(config, SBBAccessTimeConfigGroup.GROUP_NAME, SBBAccessTimeConfigGroup.class);

		if (accessTimeConfigGroup.getInsertingAccessEgressWalk()) {
			config.plansCalcRoute().setAccessEgressType(AccessEgressType.walkConstantTimeToLink);
			Collection<String> modes = accessTimeConfigGroup.getModesWithAccessTime();
			final Set<String> routedModes = new HashSet<>(ConfigUtils.addOrGetModule(config, SBBNetworkRoutingConfigGroup.class).getNetworkRoutingModes());
			routedModes.addAll(config.qsim().getMainModes());

			for (final String mode : modes) {
				if (routedModes.contains(mode)) {
					addRoutingModuleBinding(mode).toProvider(new NetworkRoutingProvider(mode));
				}

			}
		}
	}

}
