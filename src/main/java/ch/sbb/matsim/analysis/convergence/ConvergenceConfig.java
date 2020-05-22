package ch.sbb.matsim.analysis.convergence;

import org.matsim.core.config.ReflectiveConfigGroup;
import org.matsim.core.utils.collections.CollectionUtils;

import java.util.*;
import java.util.stream.Collectors;

public class ConvergenceConfig extends ReflectiveConfigGroup {

    public static final String GROUP_NAME = "ConvergenceStats";
    private static final String ITERATION_WINDOW_SIZE_PARAM = "iterationWindowSize";
    private static final String TESTS_TO_RUN_PARAM = "testsToRun";
    private static final String CONVERGENCE_CRITERION_FUNCTION_TARGET_PARAM = "convergenceCriterionFunctionTarget";

    private boolean activateConvergenceStats = false;
    private double iterationWindowSize = 60.0;
    private Test[] testsToRun = new Test[] {Test.KENDALL, Test.KS_NORMAL, Test.CV};
    private double convergenceCriterionFunctionTarget = 0.0;

    public enum Test {KENDALL, KS_NORMAL, CV}

    public ConvergenceConfig()  {
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

    @StringGetter(TESTS_TO_RUN_PARAM)
    public String getTestsToRunString() {
        return CollectionUtils.setToString(Arrays.stream(this.testsToRun).map(t -> t.name().toLowerCase()).collect(Collectors.toSet()));
    }
    @StringSetter(TESTS_TO_RUN_PARAM)
    public void setTestsToRun(String testsToRun) {
        this.testsToRun = CollectionUtils.stringToSet(testsToRun).stream().map(t -> Test.valueOf(t.toUpperCase())).toArray(Test[]::new);
    }

    public Test[] getTestsToRun() {
        return testsToRun;
    }

    public void setTestsToRun(Test[] testsToRun) {
        this.testsToRun = testsToRun;
    }

    @StringGetter(ITERATION_WINDOW_SIZE_PARAM)
    public double getIterationWindowSize() {
        return iterationWindowSize;
    }

    @StringSetter(ITERATION_WINDOW_SIZE_PARAM)
    public void setIterationWindowSize(double iterationWindowSize) {
        this.iterationWindowSize = iterationWindowSize;
    }

    @StringSetter(CONVERGENCE_CRITERION_FUNCTION_TARGET_PARAM)
    public void setConvergenceCriterionFunctionTarget(double convergenceCriterionFunctionTarget) {
        this.convergenceCriterionFunctionTarget = convergenceCriterionFunctionTarget;
    }

    @StringGetter(CONVERGENCE_CRITERION_FUNCTION_TARGET_PARAM)
    public double getConvergenceCriterionFunctionTarget() {
        return this.convergenceCriterionFunctionTarget;
    }

    @Override
    public Map<String, String> getComments() {
        Map<String, String> map = super.getComments();
        map.put(TESTS_TO_RUN_PARAM, "Possibilities are: " + CollectionUtils.setToString(Arrays.stream(Test.values()).map(t -> t.name().toLowerCase()).collect(Collectors.toSet())));
        map.put(ITERATION_WINDOW_SIZE_PARAM, "number of iterations to be used for calculating the convergence statistics. If between 0.0 and 1.0 the window is sized as a share of the total number of iterations from the controler.");
        map.put(CONVERGENCE_CRITERION_FUNCTION_TARGET_PARAM, "When the convergence criterion function reaches this value the simulation is terminated. 0.0 is the default and disables the convergence criterion termination. Make sure to set a big enough lastIteration param in the controler, since that value is also used as a termination criterion.");
        return map;
    }

    public Set<ConvergenceCriterionFunctionWeight> getFunctionWeights() {
        return getParameterSets(ConvergenceCriterionFunctionWeight.GROUP_NAME).stream()
                .map(g -> (ConvergenceCriterionFunctionWeight) g).collect(Collectors.toSet());
    }

    public void addConvergenceFunctionWeight(String testName, String globalStat, double weight) {
        ConvergenceCriterionFunctionWeight weightParams = new ConvergenceCriterionFunctionWeight();
        weightParams.setConvergenceTest(testName);
        weightParams.setGlobalStatistic(globalStat);
        weightParams.setFunctionWeight(weight);
        super.addParameterSet(weightParams);
    }

    public void addFunctionWeight(final ConvergenceCriterionFunctionWeight params) {
        super.addParameterSet(params);
    }

    public static class ConvergenceCriterionFunctionWeight extends ReflectiveConfigGroup {
        public static final String GROUP_NAME = "StoppingCriterionFunctionWeight";

        public static final String GLOBAL_STATISTIC_PARAM = "globalStatistic";
        public static final String CONVERGENCE_TEST_PARAM = "convergenceTest";
        public static final String FUNCTION_WEIGHT_PARAM = "functionWeight";

        private String globalStatistic = "scores";
        private String convergenceTest = Test.CV.name();
        private double functionWeight = 1.0;

        public ConvergenceCriterionFunctionWeight()  {
            super(GROUP_NAME);
        }

        @StringSetter(GLOBAL_STATISTIC_PARAM)
        public void setGlobalStatistic(String globalStatistic) {
            this.globalStatistic = globalStatistic;
        }

        @StringGetter(GLOBAL_STATISTIC_PARAM)
        public String getGlobalStatistic() {
            return this.globalStatistic;
        }

        @StringSetter(CONVERGENCE_TEST_PARAM)
        public void setConvergenceTest(String convergenceTest) {
            this.convergenceTest = convergenceTest;
        }

        @StringGetter(CONVERGENCE_TEST_PARAM)
        public String getConvergenceTest() {
            return this.convergenceTest;
        }

        @StringSetter(FUNCTION_WEIGHT_PARAM)
        public void setFunctionWeight(double weight) {
            this.functionWeight = weight;
        }

        @StringGetter(FUNCTION_WEIGHT_PARAM)
        public double getFunctionWeight() {
            return this.functionWeight;
        }

        @Override
        public Map<String, String> getComments() {
            Map<String, String> map = super.getComments();
            map.put(GLOBAL_STATISTIC_PARAM, "Possibilities are: 'scores', 'traveldistances' and any of the modes; or 'all' for applying this weight for all of the global statistics.");
            map.put(CONVERGENCE_TEST_PARAM, "Possibilities are: " + CollectionUtils.setToString(Arrays.stream(Test.values()).map(t -> t.name().toLowerCase()).collect(Collectors.toSet())));
            map.put(FUNCTION_WEIGHT_PARAM, "Weight for this term in the convergence criterion function. Defaults to 1.0. The criterion function is a linear sum of the (weighted) test results. Warning: there is no check for duplicates, each matching parameterSet becomes an added term to the function (matching done by string equality or 'all').");
            return map;
        }




    }
}
