package ch.sbb.matsim.routing.access;

import ch.sbb.matsim.config.SBBAccessTimeConfigGroup;
import ch.sbb.matsim.routing.network.SBBNetworkRoutingConfigGroup;
import ch.sbb.matsim.zones.Zone;
import ch.sbb.matsim.zones.Zones;
import ch.sbb.matsim.zones.ZonesCollection;
import ch.sbb.matsim.zones.ZonesModule;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.PlansCalcRouteConfigGroup.AccessEgressType;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.router.NetworkRoutingProvider;

public class AccessEgressModule extends AbstractModule {

	public static void prepareAccessEgressTimes(Scenario scenario) {
		ZonesCollection collection = (ZonesCollection) scenario.getScenarioElement(ZonesModule.SBB_ZONES);
		SBBAccessTimeConfigGroup accessTimeConfigGroup = ConfigUtils.addOrGetModule(scenario.getConfig(), SBBAccessTimeConfigGroup.GROUP_NAME, SBBAccessTimeConfigGroup.class);
		Id<Zones> zonesId = accessTimeConfigGroup.getZonesId();
		Zones zones = collection.getZones(zonesId);
		String attributePrefix = accessTimeConfigGroup.getAttributePrefix();

		for (var l : scenario.getNetwork().getLinks().values()) {
			Set<String> modesWithAccessTime = accessTimeConfigGroup.getModesWithAccessTime();
			Zone zone = zones.findZone(l.getCoord());
			for (var mode : modesWithAccessTime) {
				String attribute = attributePrefix + mode.toLowerCase();
				double accessTime = zone != null ? ((Number) zone.getAttribute(attribute)).intValue() : .0;
				NetworkUtils.setLinkAccessTime(l, mode, accessTime);
				NetworkUtils.setLinkEgressTime(l, mode, accessTime);
			}
		}

	}

	public static void prepareAccessEgressTimesForMode(String mode, Id<Zones> zonesId, String accessTimeZoneId, String egressTimeZoneId, Scenario scenario) {
		ZonesCollection collection = (ZonesCollection) scenario.getScenarioElement(ZonesModule.SBB_ZONES);
		Zones zones = collection.getZones(zonesId);
		for (var l : scenario.getNetwork().getLinks().values()) {
			Zone zone = zones.findZone(l.getCoord());
			double accessTime = zone != null ? ((Number) zone.getAttribute(accessTimeZoneId)).intValue() : .0;
			double egressTime = zone != null ? ((Number) zone.getAttribute(egressTimeZoneId)).intValue() : .0;
			NetworkUtils.setLinkEgressTime(l, mode, egressTime);
			NetworkUtils.setLinkAccessTime(l, mode, accessTime);

		}
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
