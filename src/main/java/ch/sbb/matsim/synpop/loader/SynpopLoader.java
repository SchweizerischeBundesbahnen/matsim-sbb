package ch.sbb.matsim.synpop.loader;

import org.matsim.api.core.v01.population.Population;
import org.matsim.facilities.ActivityFacilities;

public interface SynpopLoader {

    Population getPopulation();
    ActivityFacilities getFacilities();
    void load();
}
