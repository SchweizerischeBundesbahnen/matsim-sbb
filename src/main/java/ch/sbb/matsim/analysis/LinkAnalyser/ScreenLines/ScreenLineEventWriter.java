package ch.sbb.matsim.analysis.LinkAnalyser.ScreenLines;

import ch.sbb.matsim.analysis.EventsAnalysis;
import ch.sbb.matsim.analysis.LinkAnalyser.LinkAnalyser;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Scenario;

public class ScreenLineEventWriter extends LinkAnalyser implements EventsAnalysis {
    private static final Logger log = Logger.getLogger(ScreenLineEventWriter.class);

    private double scale;
    private String shapefilee;
    private String folder;

    public ScreenLineEventWriter(Scenario scenario, double scale, String shapefile, String folder) {
        super(scenario);
        this.scale = scale;
        this.shapefilee = shapefile;
        this.folder = folder;
    }


    @Override
    public void writeResults(boolean lastIteration) {
        this.writeScreenLines(this.shapefilee, this.folder, this.scale);
        if (lastIteration) {
            this.writeScreenLines(this.shapefilee, EventsAnalysis.getOutputFolderName(this.folder), this.scale);
        }
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
