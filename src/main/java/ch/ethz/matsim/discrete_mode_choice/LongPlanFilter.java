package ch.ethz.matsim.discrete_mode_choice;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Population;
import org.matsim.core.router.TripStructureUtils;

import java.util.Iterator;

public class LongPlanFilter {
    final private Logger logger = Logger.getLogger(LongPlanFilter.class);
    final private long maximumNumberOfTrips;
    

    public LongPlanFilter(long maximumNumberOfTrips) {
        this.maximumNumberOfTrips = maximumNumberOfTrips;
    }

    public void run(Population population) {
        Iterator<? extends Person> iterator = population.getPersons().values().iterator();

        logger.info(String.format("Removing persons with long plans (> %d trips) ...", maximumNumberOfTrips));
        long numberOfRemovedPersons = 0;
        long numberOfPersons = population.getPersons().size();

        while (iterator.hasNext()) {
            Person person = iterator.next();

            int numberOfRelevantTrips = 0;

            for (TripStructureUtils.Trip trip : TripStructureUtils.getTrips(person.getSelectedPlan())) {
                if (!trip.getOriginActivity().getType().equals("outside")
                        && !trip.getDestinationActivity().getType().equals("outside")) {
                    numberOfRelevantTrips++;
                }
            }

            if (numberOfRelevantTrips > maximumNumberOfTrips) {
                iterator.remove();
                population.getAttributes().removeAttribute(person.getId().toString());
                numberOfRemovedPersons++;
            }
        }

        logger.info(String.format("Removed %d/%d persons with long trips (%.2f%%)", numberOfRemovedPersons,
                numberOfPersons, 100.0 * numberOfRemovedPersons / numberOfPersons));
    }
}
