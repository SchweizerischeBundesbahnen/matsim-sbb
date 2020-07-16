package ch.sbb.matsim.zones;

import org.matsim.api.core.v01.Scenario;
import org.matsim.core.controler.AbstractModule;

/**
 * @author mrieser
 */
public class ZonesModule extends AbstractModule {

	public static final String SBB_ZONES = "SBBZones";
	private final Scenario scenario;

	public ZonesModule(Scenario scenario) {
		this.scenario = scenario;
	}

	public static void addZonestoScenario(Scenario scenario) {
		ZonesCollection zonesCollection = new ZonesCollection();
		ZonesLoader.loadAllZones(scenario.getConfig(), zonesCollection);
		scenario.addScenarioElement(SBB_ZONES, zonesCollection);
	}

	@Override
	public void install() {
		ZonesCollection zonesCollection = (ZonesCollection) scenario.getScenarioElement(SBB_ZONES);
		bind(ZonesCollection.class).toInstance(zonesCollection);
	}

}
