package ch.sbb.matsim.synpop.reader;

import org.matsim.api.core.v01.population.Population;
import org.matsim.facilities.ActivityFacilities;

public interface SynpopReader {

    Population getPopulation();
    ActivityFacilities getFacilities();
    void load();
}
