package ch.sbb.matsim.analysis.modalsplit;

import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;

/**
 * Single-responsibility contract for per-person trip analysis.
 * <p>
 * Implementations collect statistics across all persons and then write them to CSV.
 */
public interface TripDataCollector {

    /**
     * Processes one person's experienced plan.
     *
     * @param person         the person from the base population (for attributes such as subpopulation, car availability, …)
     * @param experiencedPlan the plan that was actually executed in the simulation
     */
    void collect(Person person, Plan experiencedPlan);

    /**
     * Writes the collected statistics to one or more CSV files.
     *
     * @param outputBaseDir  directory + run-prefix, e.g. {@code /path/to/output/runId.SBB_}
     * @param runId          the run identifier used in the runID column
     * @param sampleSize     simulation sample size used for scaling counts
     */
    void write(String outputBaseDir, String runId, double sampleSize);
}

