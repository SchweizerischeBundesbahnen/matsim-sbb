package ch.sbb.matsim.analysis.convergence;

import org.matsim.core.config.ReflectiveConfigGroup;

import java.util.Map;

public class ConvergenceStatsConfig extends ReflectiveConfigGroup {

    public static final String GROUP_NAME = "ConvergenceStats";
    private boolean activateConvergenceStats = false;
    private int numWindows = 30;
    private int windowSize = 10;

    public ConvergenceStatsConfig()  {
        super(GROUP_NAME);
    }

    @StringGetter("activateConvergenceStats")
    public boolean isActivateConvergenceStats() {
        return activateConvergenceStats;
    }

    @StringSetter("activateConvergenceStats")
    public void setActivateConvergenceStats(boolean activateConvergenceStats) {
        this.activateConvergenceStats = activateConvergenceStats;
    }

    @StringGetter("windowSize")
    public int getWindowSize() {
        return windowSize;
    }

    @StringSetter("windowSize")
    public void setWindowSize(int windowSize) {
        this.windowSize = windowSize;
    }

    @StringGetter("numWindows")
    public int getNumWindows() {
        return numWindows;
    }

    @StringSetter("numWindows")
    public void setNumWindows(int numWindows) {
        this.numWindows = numWindows;
    }

    @Override
    public Map<String, String> getComments() {
        Map<String, String> map = super.getComments();
        map.put("windowSize", "number of iterations to be averaged in the windows used for the convergence statistics");
        map.put("numWindows", "minimum number of windows for running the convergence tests. Outputs start at iteration numWindows*windowSize.");
        return map;
    }
}
