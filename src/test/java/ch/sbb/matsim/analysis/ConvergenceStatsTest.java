package ch.sbb.matsim.analysis;

import ch.sbb.matsim.analysis.convergence.ConvergenceStats;
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
import org.matsim.core.config.groups.ControlerConfigGroup;
import org.matsim.core.controler.*;
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
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;

public class ConvergenceStatsTest {

    @Rule
    public MatsimTestUtils utils = new MatsimTestUtils();

    @Test
    public void test_StationarityTests() throws IOException {
        ConvergenceStats cs = new ConvergenceStats(10, 30);
        double[] scores = ConvergenceStats.loadGlobalStats( utils.getPackageInputDirectory() + "convergence/scorestats.txt");
        for (ConvergenceStats.Test test : ConvergenceStats.Test.values()) {
            Map.Entry res = cs.runTest(test, scores);
            System.out.println(res);
        }
    }

    @Test
    public void test_StationarityTestsOutput() throws IOException {
        FileUtils.copyDirectory(new File(utils.getPackageInputDirectory() + "convergence"), new File(utils.getOutputDirectory()));
        ConvergenceStats cs = new ConvergenceStats(10, 30);
        IterationStartsEvent event = new IterationStartsEvent(new StubControler(), 301);
        cs.notifyIterationStarts(event);
        cs.close();
        for (ConvergenceStats.Test test : ConvergenceStats.Test.values()) {
            Assert.assertTrue(Files.exists(Paths.get(utils.getOutputDirectory(), "convergence", test.name().toLowerCase() + ".txt")));
            BufferedReader br = new BufferedReader(new FileReader(Paths.get(utils.getOutputDirectory(), "convergence", test.name().toLowerCase() + ".txt").toFile()));
            br.readLine(); // skip header
            Assert.assertNotNull(br.readLine());
        }
    }

    private class StubControler implements MatsimServices {
        @Override
        public OutputDirectoryHierarchy getControlerIO() {
            return new OutputDirectoryHierarchy(utils.getOutputDirectory(),
                    OutputDirectoryHierarchy.OverwriteFileSetting.overwriteExistingFiles, ControlerConfigGroup.CompressionType.none);
        }
        public Integer getIterationNumber(){return null;}
        public IterationStopWatch getStopwatch(){return null;}
        public TravelTime getLinkTravelTimes(){return null;}
        public Provider<TripRouter> getTripRouterProvider(){return null;}
        public TravelDisutility createTravelDisutilityCalculator(){return null;}
        public LeastCostPathCalculatorFactory getLeastCostPathCalculatorFactory(){return null;}
        public ScoringFunctionFactory getScoringFunctionFactory(){return null;}
        public Config getConfig(){return null;}
        public Scenario getScenario(){return null;}
        public EventsManager getEvents(){return null;}
        public com.google.inject.Injector getInjector(){return null;}
        public CalcLinkStats getLinkStats(){return null;}
        public VolumesAnalyzer getVolumes(){return null;}
        public ScoreStats getScoreStats(){return null;}
        public TravelDisutilityFactory getTravelDisutilityFactory(){return null;}
        public StrategyManager getStrategyManager(){return null;}
        public void addControlerListener(ControlerListener controlerListener){}
    }

}
