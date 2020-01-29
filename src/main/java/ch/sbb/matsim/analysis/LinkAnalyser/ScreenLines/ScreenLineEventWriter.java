package ch.sbb.matsim.analysis.LinkAnalyser.ScreenLines;

import ch.sbb.matsim.analysis.EventsAnalysis;
import ch.sbb.matsim.analysis.LinkAnalyser.LinkAnalyser;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Scenario;

public class ScreenLineEventWriter extends LinkAnalyser implements EventsAnalysis {
    private final static Logger log = Logger.getLogger(ScreenLineEventWriter.class);

    double scale;
    String shapefilee;
    String folder;

    public ScreenLineEventWriter(Scenario scenario, double scale, String shapefile, String folder) {
        super(scenario);
        this.scale = scale;
        this.shapefilee = shapefile;
        this.folder = folder;
    }


    @Override
    public void writeResults() {
        this.writeScreenLines(this.shapefilee, this.folder, this.scale);
    }


    public void writeScreenLines(String shapefile, String folder, double scale) {
        ScreenLinesAnalyser sla = new ScreenLinesAnalyser(this.scenario, shapefile);
        sla.write(folder, this.linkVolumes, scale);
    }


    // Methods
    @Override
    public void reset(int iteration) {
    }
}
