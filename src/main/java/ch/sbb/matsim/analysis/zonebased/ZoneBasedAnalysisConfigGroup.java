package ch.sbb.matsim.analysis.zonebased;

import org.matsim.core.config.ConfigGroup;
import org.matsim.core.config.ReflectiveConfigGroup;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author jbischoff / SBB
 */
public class ZoneBasedAnalysisConfigGroup extends ReflectiveConfigGroup {
    public static final String GROUPNAME = "zonesAnalysis";
    private static final String ZONESFILE = "zonesFile";
    private static final String ZONESID = "zonesIdAttribute";
    private static final String REPORTFILE = "reportFile";

    private String zonesFile = null;
    private String zonesIdAttribute = "ID";
    private String reportFile = "report.csv";

    public ZoneBasedAnalysisConfigGroup() {
        super(GROUPNAME);
    }

    @StringGetter(REPORTFILE)
    public String getReportFile() {
        return reportFile;
    }

    @StringSetter(REPORTFILE)
    public void setReportFile(String reportFile) {
        this.reportFile = reportFile;
    }

    @StringGetter(ZONESFILE)
    public String getZonesFile() {
        return zonesFile;
    }

    @StringSetter(ZONESFILE)
    public void setZonesFile(String zonesFile) {
        this.zonesFile = zonesFile;
    }

    @StringGetter(ZONESID)
    public String getZonesIdAttribute() {
        return zonesIdAttribute;
    }

    @StringSetter(ZONESID)
    public void setZonesIdAttribute(String zonesIdAttribute) {
        this.zonesIdAttribute = zonesIdAttribute;
    }

    public void addRun(AnalysisRunParameterSet set) {
        super.addParameterSet(set);
    }

    public Collection<AnalysisRunParameterSet> getRuns() {
        List<AnalysisRunParameterSet> runsGroups = new ArrayList<>();
        for (ConfigGroup pars : getParameterSets(AnalysisRunParameterSet.TYPE)) {
            runsGroups.add((AnalysisRunParameterSet) pars);
        }
        return runsGroups;
    }

    @Override
    public ConfigGroup createParameterSet(String type) {
        if (AnalysisRunParameterSet.TYPE.equals(type)) {
            return new AnalysisRunParameterSet();
        }
        throw new IllegalArgumentException("Unsupported parameterset-type: " + type);
    }

    @Override
    public void addParameterSet(ConfigGroup set) {
        if (set instanceof AnalysisRunParameterSet) {
            addRun((AnalysisRunParameterSet) set);
        } else {
            throw new IllegalArgumentException("Unsupported parameterset: " + set.getClass().getName());
        }
    }

    public static class AnalysisRunParameterSet extends ReflectiveConfigGroup {
        static final String TYPE = "run";
        private static final String RUNFOLDERSTR = "runFolder";
        private static final String RUNIDSTR = "runId";
        private String runFolder = null;
        private String runId = null;

        public AnalysisRunParameterSet() {
            super(TYPE);
        }

        @StringGetter(RUNFOLDERSTR)
        public String getRunFolder() {
            return runFolder;
        }

        @StringSetter(RUNFOLDERSTR)
        public void setRunFolder(String runFolder) {
            this.runFolder = runFolder;
        }

        @StringGetter(RUNIDSTR)
        public String getRunId() {
            return runId;
        }

        @StringSetter(RUNIDSTR)
        public void setRunId(String runId) {
            this.runId = runId;
        }


    }


}
