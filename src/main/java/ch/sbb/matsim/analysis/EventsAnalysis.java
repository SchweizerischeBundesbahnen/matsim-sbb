package ch.sbb.matsim.analysis;

import org.matsim.core.events.handler.EventHandler;

public interface EventsAnalysis extends EventHandler {

    void writeResults();

}
