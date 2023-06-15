package ch.sbb.matsim.routing.pt.raptor;

import org.matsim.api.core.v01.population.Person;
import org.matsim.core.router.RoutingModule;
import org.matsim.facilities.Facility;

public interface AccessEgressRouteCache {


    RouteCharacteristics getCachedRouteCharacteristics(String mode, Facility stopFacility, Facility actFacility, RoutingModule module, Person person);

    record RouteCharacteristics(double distance, double accessTime, double egressTime, double travelTime) {

    }
}
