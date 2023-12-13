package ch.sbb.matsim.analysis;

import ch.sbb.matsim.RunSBB;
import ch.sbb.matsim.analysis.convergence.ConvergenceConfigGroup;
import ch.sbb.matsim.analysis.convergence.ConvergenceStats;
import ch.sbb.matsim.config.PostProcessingConfigGroup;
import com.google.inject.Provider;
import org.apache.commons.io.FileUtils;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.matsim.analysis.CalcLinkStats;
import org.matsim.analysis.IterationStopWatch;
import org.matsim.analysis.ScoreStats;
import org.matsim.analysis.VolumesAnalyzer;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.ControllerConfigGroup;
import org.matsim.core.controler.MatsimServices;
import org.matsim.core.controler.OutputDirectoryHierarchy;
import org.matsim.core.controler.events.IterationStartsEvent;
import org.matsim.core.controler.listener.ControlerListener;
import org.matsim.core.replanning.StrategyManager;
import org.matsim.core.router.TripRouter;
import org.matsim.core.router.costcalculators.TravelDisutilityFactory;
import org.matsim.core.router.util.LeastCostPathCalculatorFactory;
import org.matsim.core.router.util.TravelDisutility;
import org.matsim.core.router.util.TravelTime;
import org.matsim.core.scoring.ScoringFunctionFactory;
import org.matsim.testcases.MatsimTestUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ConvergenceStatsTest {

	@Rule
	public MatsimTestUtils utils = new MatsimTestUtils();

	@Test
	public void test_ConvergenceTests() throws IOException {
		ConvergenceStats cs = new ConvergenceStats(60, ConvergenceConfigGroup.Test.values(), ConfigUtils.createConfig());
		double[] scores = ConvergenceStats.loadGlobalStats(utils.getPackageInputDirectory() + "convergence/traveldistancestats.csv");
		System.out.println("Test: statistic=p-value");
		for (ConvergenceConfigGroup.Test test : ConvergenceConfigGroup.Test.values()) {
			Map.Entry<Double, Double> res = cs.runTest(test, scores);
			System.out.print(test.name() + ": ");
			System.out.println(res);
			Assert.assertTrue(Double.isFinite(res.getKey()));
			Assert.assertTrue(Double.isFinite(res.getValue()) || Double.isNaN(res.getValue()));
		}
	}

	@Test
	public void test_ConvergenceTestsOutput() throws IOException {
		FileUtils.copyDirectory(new File(utils.getPackageInputDirectory() + "convergence"), new File(utils.getOutputDirectory()));
		ConvergenceStats cs = new ConvergenceStats(60, ConvergenceConfigGroup.Test.values(), ConfigUtils.createConfig());
		IterationStartsEvent event = new IterationStartsEvent(new StubControler(), 301, true);
		cs.notifyIterationStarts(event);
		cs.close();
		for (ConvergenceConfigGroup.Test test : ConvergenceConfigGroup.Test.values()) {
			File file = Paths.get(utils.getOutputDirectory(), "convergence", test.name().toLowerCase() + ".csv").toFile();
			Assert.assertTrue(file.exists());
			BufferedReader br = new BufferedReader(new FileReader(file));
			br.readLine(); // skip header
			Assert.assertNotNull(br.readLine());
		}
	}

	@Test
	public void test_convergenceCriterion() throws IOException {
		System.setProperty("matsim.preferLocalDtds", "true");
		Config config = RunSBB.buildConfig("test/input/scenarios/mobi31test/config.xml");
		config.controller().setMobsim("qsim");
		// integrate config
		ConvergenceConfigGroup csConfig = ConfigUtils.addOrGetModule(config, ConvergenceConfigGroup.class);
		csConfig.setActivateConvergenceStats(true);
		csConfig.setIterationWindowSize(2);
		csConfig.setTestsToRun(ConvergenceConfigGroup.Test.values());

		// setup convergence criterion weights and target (weight stats equally but only consider CV)
		csConfig.addConvergenceFunctionWeight(ConvergenceConfigGroup.Test.CV.name(), "all", 0.1);
		csConfig.addConvergenceFunctionWeight(ConvergenceConfigGroup.Test.KS_NORMAL.name(), "all", 0.0);
		csConfig.addConvergenceFunctionWeight(ConvergenceConfigGroup.Test.KENDALL.name(), "all", 0.0);
		csConfig.setConvergenceCriterionFunctionTarget(0.07); // should stop at 3 or 4 iterations

		// shut-off outputs
		int absoluteLastIteration = 10;
		config.controller().setLastIteration(absoluteLastIteration);
		config.controller().setOutputDirectory(utils.getOutputDirectory());
		config.controller().setWriteEventsInterval(0);
		config.controller().setWritePlansInterval(0);
		config.controller().setWriteSnapshotsInterval(0);
		config.controller().setDumpDataAtEnd(false);
		config.controller().setCreateGraphs(false);
		ConfigUtils.addOrGetModule(config, PostProcessingConfigGroup.class).setAllPostProcessingOff();

		// quick simulation is enough
		config.qsim().setStartTime(3600 * 10.0);
		config.qsim().setEndTime(3600 * 11.0);
		config.qsim().setTimeStepSize(1.0);
		RunSBB.run(config);

		// tests
		int iterationsRun = 0;
		for (ConvergenceConfigGroup.Test test : ConvergenceConfigGroup.Test.values()) {
			File file = Paths.get(utils.getOutputDirectory(), "convergence", test.name().toLowerCase() + ".csv").toFile();
			Assert.assertTrue(file.exists());
			List<String> lines = new BufferedReader(new FileReader(file)).lines().collect(Collectors.toList());
			iterationsRun = lines.size();
			Assert.assertNotNull(lines.get(1));
		}

	}

	private class StubControler implements MatsimServices {

		@Override
		public OutputDirectoryHierarchy getControlerIO() {
			return new OutputDirectoryHierarchy(utils.getOutputDirectory(),
					OutputDirectoryHierarchy.OverwriteFileSetting.overwriteExistingFiles, ControllerConfigGroup.CompressionType.none);
		}

		@Override
		public Integer getIterationNumber() {
			return null;
		}

		@Override
		public IterationStopWatch getStopwatch() {
			return null;
		}

		@Override
		public TravelTime getLinkTravelTimes() {
			return null;
		}

		@Override
		public Provider<TripRouter> getTripRouterProvider() {
			return null;
		}

		@Override
		public TravelDisutility createTravelDisutilityCalculator() {
			return null;
		}

		@Override
		public LeastCostPathCalculatorFactory getLeastCostPathCalculatorFactory() {
			return null;
		}

		@Override
		public ScoringFunctionFactory getScoringFunctionFactory() {
			return null;
		}

		@Override
		public Config getConfig() {
			return null;
		}

		@Override
		public Scenario getScenario() {
			return null;
		}

		@Override
		public EventsManager getEvents() {
			return null;
		}

		@Override
		public com.google.inject.Injector getInjector() {
			return null;
		}

		@Override
		public CalcLinkStats getLinkStats() {
			return null;
		}

		@Override
		public VolumesAnalyzer getVolumes() {
			return null;
		}

		@Override
		public ScoreStats getScoreStats() {
			return null;
		}

		@Override
		public TravelDisutilityFactory getTravelDisutilityFactory() {
			return null;
		}

		@Override
		public StrategyManager getStrategyManager() {
			return null;
		}

		@Override
		public void addControlerListener(ControlerListener controlerListener) {
		}
	}

}
