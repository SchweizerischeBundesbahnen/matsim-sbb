package ch.sbb.matsim.plans.models;

public interface ModelStep extends Runnable {
    /**
     * @return The unique key for this model step, used in the config.yaml.
     */
    String getName();
}