package ch.sbb.matsim.preparation;

import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Population;
import org.matsim.core.config.groups.NetworkConfigGroup;
import org.matsim.core.network.NetworkUtils;

public class SBBXY2LinksAssigner {
    public static void run(Population population, Network network, NetworkConfigGroup networkConfigGroup) {
        Network accessibleNetwork = LinkToFacilityAssigner.getAccessibleLinks(network, networkConfigGroup);
        population.getPersons().values().parallelStream().flatMap(person -> person.getSelectedPlan().getPlanElements().stream()).filter(Activity.class::isInstance).filter(planElement -> ((Activity) planElement).getFacilityId() == null).forEach(planElement -> {
            Activity activity = (Activity) planElement;
            activity.setLinkId(NetworkUtils.getNearestLink(accessibleNetwork, activity.getCoord()).getId());
        });
    }
}
