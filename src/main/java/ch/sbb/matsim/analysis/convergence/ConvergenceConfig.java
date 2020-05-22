package ch.sbb.matsim.analysis.convergence;

import org.matsim.core.config.ReflectiveConfigGroup;
import org.matsim.core.utils.collections.CollectionUtils;

import java.util.*;
import java.util.stream.Collectors;

public class ConvergenceStatsConfig extends ReflectiveConfigGroup {

    public static final String GROUP_NAME = "ConvergenceStats";
    private boolean activateConvergenceStats = false;
    private int numWindows = 30;
    private int windowSize = 10;
    private Test[] testsToRun = new Test[] {Test.KENDALL, Test.KS_NORMAL};

    public enum Test {KENDALL, PEARSON, SPEARMAN, KS_NORMAL, KS_UNIFORM}

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

    @StringGetter("testsToRun")
    public String getTestsToRunString() {
        return CollectionUtils.setToString(Arrays.stream(this.testsToRun).map(t -> t.name().toLowerCase()).collect(Collectors.toSet()));
    }

    public Test[] getTestsToRun() {
        return testsToRun;
    }

    @StringSetter("testsToRun")
    public void setTestsToRun(String testsToRun) {
        this.testsToRun = CollectionUtils.stringToSet(testsToRun).stream().map(t -> Test.valueOf(t.toUpperCase())).toArray(Test[]::new);
    }

    public void setTestsToRun(Test[] testsToRun) {
        this.testsToRun = testsToRun;
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
        map.put("testsToRun", "Possibilities are: " + CollectionUtils.setToString(Arrays.stream(Test.values()).map(t -> t.name().toLowerCase()).collect(Collectors.toSet())));
        map.put("windowSize", "number of iterations to be averaged in the windows used for the convergence statistics");
        map.put("numWindows", "minimum number of windows for running the convergence tests. Outputs start at iteration numWindows*windowSize.");
        return map;
    }
}
