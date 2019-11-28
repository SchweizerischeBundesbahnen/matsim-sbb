package ch.sbb.matsim.intermodal.analysis;

import org.matsim.core.controler.MatsimServices;
import org.matsim.core.controler.events.IterationEndsEvent;
import org.matsim.core.controler.listener.IterationEndsListener;

import javax.inject.Inject;

/**
 * @author jbischoff / SBB
 */
public class IntermodalControlerListener implements IterationEndsListener {


    @Inject
    private IntermodalTransferTimeAnalyser analyser;

    @Inject
    private MatsimServices services;

    @Override
    public void notifyIterationEnds(IterationEndsEvent event) {
        analyser.writeIterationStats(services.getControlerIO().getIterationFilename(event.getIteration(), "intermodalTransfers"));
    }
}
