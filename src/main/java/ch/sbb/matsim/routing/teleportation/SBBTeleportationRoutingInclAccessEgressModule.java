package ch.sbb.matsim.routing.teleportation;

import java.util.ArrayList;
import java.util.List;

import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.api.core.v01.population.PopulationFactory;
import org.matsim.core.gbl.Gbl;
import org.matsim.core.router.StageActivityTypes;
import org.matsim.core.router.TeleportationRoutingModule;
import org.matsim.facilities.Facility;

import ch.sbb.matsim.analysis.LocateAct;
import ch.sbb.matsim.routing.access.AccessEgressRouting;

public class SBBTeleportationRoutingInclAccessEgressModule extends TeleportationRoutingModule {
    private AccessEgressRouting accessEgress;

    public SBBTeleportationRoutingInclAccessEgressModule(String mode, PopulationFactory populationFactory, double networkTravelSpeed, double beelineDistanceFactor, LocateAct actLocator, Network network) {
        super(mode, populationFactory, networkTravelSpeed, beelineDistanceFactor);
        this.accessEgress = new AccessEgressRouting(actLocator, populationFactory, mode, network);
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

        now = leg.getDepartureTime() + leg.getTravelTime();
        now = accessEgress.addEgress(toFacility, egressActLink, now, result, person);

        return result;
    }

    @Override
    public StageActivityTypes getStageActivityTypes() {
        return this.accessEgress.getStageActivityTypes();
    }

}
