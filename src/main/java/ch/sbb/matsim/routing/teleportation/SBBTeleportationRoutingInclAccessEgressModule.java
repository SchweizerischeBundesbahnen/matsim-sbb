package ch.sbb.matsim.routing.teleportation;

import ch.sbb.matsim.routing.access.AccessEgressRouting;
import ch.sbb.matsim.zones.Zones;
import java.util.ArrayList;
import java.util.List;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.core.gbl.Gbl;
import org.matsim.core.router.TeleportationRoutingModule;
import org.matsim.facilities.Facility;

/**
 * Based on org.matsim.core.router.NetworkRoutingInclAccessEgressModule
 */

public class SBBTeleportationRoutingInclAccessEgressModule extends TeleportationRoutingModule {
    private AccessEgressRouting accessEgress;

    public SBBTeleportationRoutingInclAccessEgressModule(String mode, Scenario scenario, double networkTravelSpeed, double beelineDistanceFactor, Zones zones,
                                                         Network network) {
        super(mode, scenario, networkTravelSpeed, beelineDistanceFactor);
        this.accessEgress = new AccessEgressRouting(zones, scenario.getPopulation().getFactory(), mode, network);
    }

    @Override
    public List<? extends PlanElement> calcRoute(Facility fromFacility, Facility toFacility, double departureTime, Person person) {

        Gbl.assertNotNull(fromFacility);
        Gbl.assertNotNull(toFacility);

        Link accessActLink = accessEgress.decideOnLink(fromFacility);
        Link egressActLink = accessEgress.decideOnLink(toFacility);

		double now = departureTime;

		List<PlanElement> result = new ArrayList<>();

		now = accessEgress.addAccess(fromFacility, accessActLink, now, result, person);
		List<? extends PlanElement> planElements = super.calcRoute(fromFacility, toFacility, now, person);
		Leg leg = (Leg) planElements.get(0);
		result.add(leg);

		now = leg.getDepartureTime().seconds() + leg.getTravelTime().seconds();
		now = accessEgress.addEgress(toFacility, egressActLink, now, result, person);

		return result;
	}

}
