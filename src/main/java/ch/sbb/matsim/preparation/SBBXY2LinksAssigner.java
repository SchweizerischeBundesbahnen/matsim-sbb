package ch.sbb.matsim.preparation;

import org.apache.logging.log4j.LogManager;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Population;
import org.matsim.core.config.groups.NetworkConfigGroup;
import org.matsim.core.network.NetworkUtils;

import java.util.concurrent.atomic.AtomicInteger;

public class SBBXY2LinksAssigner {
    public static void run(Population population, Network network, NetworkConfigGroup networkConfigGroup) {
        Network accessibleNetwork = LinkToFacilityAssigner.getAccessibleLinks(network, networkConfigGroup);
        AtomicInteger i = new AtomicInteger();
        population.getPersons().values().parallelStream().flatMap(person -> person.getSelectedPlan().getPlanElements().stream()).filter(Activity.class::isInstance).filter(planElement -> ((Activity) planElement).getFacilityId() == null).forEach(planElement -> {
            Activity activity = (Activity) planElement;
            activity.setLinkId(NetworkUtils.getNearestLink(accessibleNetwork, activity.getCoord()).getId());
            i.incrementAndGet();
        });
        LogManager.getLogger(SBBXY2LinksAssigner.class).info("Assigned " + i.get() + " links to plans.");
    }
}
