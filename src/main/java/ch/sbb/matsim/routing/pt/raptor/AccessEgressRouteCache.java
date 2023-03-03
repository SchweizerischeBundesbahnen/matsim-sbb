package ch.sbb.matsim.routing.pt.raptor;

import org.matsim.api.core.v01.population.Person;
import org.matsim.core.router.RoutingModule;
import org.matsim.facilities.Facility;

public interface AccessEgressRouteCache {


    RouteCharacteristics getCachedRouteCharacteristics(String mode, Facility stopFacility, Facility actFacility, RoutingModule module, Person person);

    class RouteCharacteristics {

        private final double distance;
        private final double accessTime;
        private final double egressTime;
        private final double travelTime;

        public RouteCharacteristics(double distance, double accessTime, double egressTime, double travelTime) {
            this.distance = distance;
            this.accessTime = accessTime;
            this.egressTime = egressTime;
            this.travelTime = travelTime;
        }

        public double getDistance() {
            return distance;
        }

        public double getAccessTime() {
            return accessTime;
        }

        public double getEgressTime() {
            return egressTime;
        }

        public double getTravelTime() {
            return travelTime;
        }

    }
}
