package ch.sbb.matsim.replanning;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.StrategyConfigGroup;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.controler.Controler;
import org.matsim.core.replanning.StrategyManager;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.io.IOUtils;
import org.matsim.testcases.MatsimTestUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.List;

public class SimpleAnnealerTest {

    private Scenario scenario;
    private Config config;
    private SimpleAnnealerConfigGroup saConfig;
    private SimpleAnnealerConfigGroup.AnnealingVariable saConfigVar;
    private static final String FILENAME_ANNEAL = "annealingRates.csv";

    @Rule
    public MatsimTestUtils utils = new MatsimTestUtils();

    @Before
    public void setup() {
        this.saConfig = new SimpleAnnealerConfigGroup();
        this.saConfigVar = new SimpleAnnealerConfigGroup.AnnealingVariable();
        this.saConfig.addAnnealingVariable(this.saConfigVar);
        this.config = ConfigUtils.createConfig();
        this.config.addModule(this.saConfig);

        StrategyConfigGroup.StrategySettings s1 = new StrategyConfigGroup.StrategySettings();
        s1.setStrategyName("ReRoute");
        s1.setWeight(0.25);
        this.config.strategy().addStrategySettings(s1);
        StrategyConfigGroup.StrategySettings s2 = new StrategyConfigGroup.StrategySettings();
        s2.setStrategyName("SubtourModeChoice");
        s2.setWeight(0.25);
        this.config.strategy().addStrategySettings(s2);
        StrategyConfigGroup.StrategySettings s3 = new StrategyConfigGroup.StrategySettings();
        s3.setStrategyName("ChangeExpBeta"); // shouldn't be affected
        s3.setWeight(0.5);
        this.config.strategy().addStrategySettings(s3);

        this.config.controler().setCreateGraphs(false);
        this.config.controler().setDumpDataAtEnd(false);
        this.config.controler().setWriteEventsInterval(0);
        this.config.controler().setWritePlansInterval(0);
        this.config.controler().setWriteSnapshotsInterval(0);
        this.config.controler().setLastIteration(10);
        this.config.controler().setOutputDirectory(this.utils.getOutputDirectory() + "annealOutput");

        this.scenario = ScenarioUtils.createScenario(this.config);
    }

    @Test
    public void testLinearAnneal() throws IOException {
        this.saConfigVar.setAnnealType("linear");
        this.saConfigVar.setEndValue(0.0);

        Controler controler = new Controler(this.scenario);
        controler.addOverridingModule(new AbstractModule() {
            @Override
            public void install() {addControlerListenerBinding().to(SimpleAnnealer.class);}});
        controler.run();

        Assert.assertEquals(expectedLinearAnneal, readResult(controler.getControlerIO().getOutputFilename(FILENAME_ANNEAL)));

        StrategyManager sm = controler.getInjector().getInstance(StrategyManager.class);
        List<Double> weights = sm.getWeights(null);

        Assert.assertEquals(0.5, weights.stream().mapToDouble(Double::doubleValue).sum(), 1e-4);
    }

    @Test
    public void testMsaAnneal() throws IOException {
        this.saConfigVar.setAnnealType("msa");
        this.saConfigVar.setShapeFactor(1.0);

        Controler controler = new Controler(this.scenario);
        controler.addOverridingModule(new AbstractModule() {
            @Override
            public void install() {addControlerListenerBinding().to(SimpleAnnealer.class);}});
        controler.run();

        Assert.assertEquals(expectedMsaAnneal, readResult(controler.getControlerIO().getOutputFilename(FILENAME_ANNEAL)));

        StrategyManager sm = controler.getInjector().getInstance(StrategyManager.class);
        List<Double> weights = sm.getWeights(null);

        Assert.assertEquals(0.55, weights.stream().mapToDouble(Double::doubleValue).sum(), 1e-4);
    }

    @Test
    public void testGeometricAnneal() throws IOException {
        this.saConfigVar.setAnnealType("geometric");
        this.saConfigVar.setShapeFactor(0.9);

        Controler controler = new Controler(this.scenario);
        controler.addOverridingModule(new AbstractModule() {
            @Override
            public void install() {addControlerListenerBinding().to(SimpleAnnealer.class);}});
        controler.run();

        Assert.assertEquals(expectedGeometricAnneal, readResult(controler.getControlerIO().getOutputFilename(FILENAME_ANNEAL)));

        StrategyManager sm = controler.getInjector().getInstance(StrategyManager.class);
        List<Double> weights = sm.getWeights(null);

        Assert.assertEquals(0.6743, weights.stream().mapToDouble(Double::doubleValue).sum(), 1e-4);
    }

    @Test
    public void testExponentialAnneal() throws IOException {
        this.saConfigVar.setAnnealType("exponential");
        this.saConfigVar.setHalfLife(5);

        Controler controler = new Controler(this.scenario);
        controler.addOverridingModule(new AbstractModule() {
            @Override
            public void install() {addControlerListenerBinding().to(SimpleAnnealer.class);}});
        controler.run();

        Assert.assertEquals(expectedExponentialAnneal, readResult(controler.getControlerIO().getOutputFilename(FILENAME_ANNEAL)));

        StrategyManager sm = controler.getInjector().getInstance(StrategyManager.class);
        List<Double> weights = sm.getWeights(null);

        Assert.assertEquals(0.5677, weights.stream().mapToDouble(Double::doubleValue).sum(), 1e-4);
    }

    @Test
    public void testSigmoidAnneal() throws IOException {
        this.saConfigVar.setAnnealType("sigmoid");
        this.saConfigVar.setHalfLife(5);
        this.saConfigVar.setShapeFactor(1.0);

        Controler controler = new Controler(this.scenario);
        controler.addOverridingModule(new AbstractModule() {
            @Override
            public void install() {addControlerListenerBinding().to(SimpleAnnealer.class);}});
        controler.run();

        Assert.assertEquals(expectedSigmoidAnneal, readResult(controler.getControlerIO().getOutputFilename(FILENAME_ANNEAL)));

        StrategyManager sm = controler.getInjector().getInstance(StrategyManager.class);
        List<Double> weights = sm.getWeights(null);

        Assert.assertEquals(0.5034, weights.stream().mapToDouble(Double::doubleValue).sum(), 1e-4);
    }

    @Test
    public void testParameterAnneal() throws IOException {
        this.saConfigVar.setAnnealType("linear");
        this.saConfigVar.setAnnealParameter("BrainExpBeta");
        this.saConfigVar.setEndValue(0.0);

        Controler controler = new Controler(this.scenario);
        controler.addOverridingModule(new AbstractModule() {
            @Override
            public void install() {addControlerListenerBinding().to(SimpleAnnealer.class);}});
        controler.run();

        Assert.assertEquals(expectedParameterAnneal, readResult(controler.getControlerIO().getOutputFilename(FILENAME_ANNEAL)));
        Assert.assertEquals(0.0, controler.getConfig().planCalcScore().getBrainExpBeta(), 1e-4);
    }

    @Test
    public void testTwoParameterAnneal() throws IOException {
        this.saConfigVar.setAnnealType("msa");
        this.saConfigVar.setShapeFactor(1.0);

        SimpleAnnealerConfigGroup.AnnealingVariable otherAv = new SimpleAnnealerConfigGroup.AnnealingVariable();
        otherAv.setAnnealType("linear");
        otherAv.setEndValue(0.0);
        otherAv.setAnnealParameter("BrainExpBeta");
        this.saConfig.addAnnealingVariable(otherAv);

        Controler controler = new Controler(this.scenario);
        controler.addOverridingModule(new AbstractModule() {
            @Override
            public void install() {addControlerListenerBinding().to(SimpleAnnealer.class);}});
        controler.run();

        Assert.assertEquals(expectedTwoParameterAnneal, readResult(controler.getControlerIO().getOutputFilename(FILENAME_ANNEAL)));
        Assert.assertEquals(0.0, controler.getConfig().planCalcScore().getBrainExpBeta(), 1e-4);

        StrategyManager sm = controler.getInjector().getInstance(StrategyManager.class);
        List<Double> weights = sm.getWeights(null);

        Assert.assertEquals(0.55, weights.stream().mapToDouble(Double::doubleValue).sum(), 1e-4);
    }

    @Test
    public void testInnovationSwitchoffAnneal() throws IOException {
        this.config.strategy().setFractionOfIterationsToDisableInnovation(0.5);
        this.saConfigVar.setAnnealType("msa");
        this.saConfigVar.setShapeFactor(1.0);

        Controler controler = new Controler(this.scenario);
        controler.addOverridingModule(new AbstractModule() {
            @Override
            public void install() {addControlerListenerBinding().to(SimpleAnnealer.class);}});
        controler.run();

        Assert.assertEquals(expectedInnovationSwitchoffAnneal, readResult(controler.getControlerIO().getOutputFilename(FILENAME_ANNEAL)));

        StrategyManager sm = controler.getInjector().getInstance(StrategyManager.class);
        List<Double> weights = sm.getWeights(null);

        Assert.assertEquals(0.5, weights.stream().mapToDouble(Double::doubleValue).sum(), 1e-4);
    }

    @Test
    public void testFreezeEarlyAnneal() throws IOException {
        this.saConfigVar.setAnnealType("msa");
        this.saConfigVar.setShapeFactor(1.0);
        this.saConfigVar.setEndValue(0.1);

        Controler controler = new Controler(this.scenario);
        controler.addOverridingModule(new AbstractModule() {
            @Override
            public void install() {addControlerListenerBinding().to(SimpleAnnealer.class);}});
        controler.run();

        Assert.assertEquals(expectedFreezeEarlyAnneal, readResult(controler.getControlerIO().getOutputFilename(FILENAME_ANNEAL)));

        StrategyManager sm = controler.getInjector().getInstance(StrategyManager.class);
        List<Double> weights = sm.getWeights(null);

        Assert.assertEquals(0.6, weights.stream().mapToDouble(Double::doubleValue).sum(), 1e-4);
    }

    private static String readResult(String filePath) throws IOException {
        BufferedReader br = IOUtils.getBufferedReader(filePath);
        StringBuilder sb = new StringBuilder();
        String line = br.readLine();

        while (line != null) {
            sb.append(line);
            sb.append("\n");
            line = br.readLine();
        }

        return sb.toString();
    }

    private String expectedLinearAnneal =
            "it;globalInnovationRate;ReRoute;SubtourModeChoice;ChangeExpBeta\n" +
                    "0;0.5000;0.2500;0.2500;0.5000\n" +
                    "1;0.4500;0.2250;0.2250;0.5000\n" +
                    "2;0.4000;0.2000;0.2000;0.5000\n" +
                    "3;0.3500;0.1750;0.1750;0.5000\n" +
                    "4;0.3000;0.1500;0.1500;0.5000\n" +
                    "5;0.2500;0.1250;0.1250;0.5000\n" +
                    "6;0.2000;0.1000;0.1000;0.5000\n" +
                    "7;0.1500;0.0750;0.0750;0.5000\n" +
                    "8;0.1000;0.0500;0.0500;0.5000\n" +
                    "9;0.0500;0.0250;0.0250;0.5000\n" +
                    "10;0.0000;0.0000;0.0000;0.5000\n";

    private String expectedMsaAnneal =
            "it;globalInnovationRate;ReRoute;SubtourModeChoice;ChangeExpBeta\n" +
                    "0;0.5000;0.2500;0.2500;0.5000\n" +
                    "1;0.5000;0.2500;0.2500;0.5000\n" +
                    "2;0.2500;0.1250;0.1250;0.5000\n" +
                    "3;0.1667;0.0833;0.0833;0.5000\n" +
                    "4;0.1250;0.0625;0.0625;0.5000\n" +
                    "5;0.1000;0.0500;0.0500;0.5000\n" +
                    "6;0.0833;0.0417;0.0417;0.5000\n" +
                    "7;0.0714;0.0357;0.0357;0.5000\n" +
                    "8;0.0625;0.0313;0.0313;0.5000\n" +
                    "9;0.0556;0.0278;0.0278;0.5000\n" +
                    "10;0.0500;0.0250;0.0250;0.5000\n";

    private String expectedGeometricAnneal =
            "it;globalInnovationRate;ReRoute;SubtourModeChoice;ChangeExpBeta\n" +
                    "0;0.5000;0.2500;0.2500;0.5000\n" +
                    "1;0.4500;0.2250;0.2250;0.5000\n" +
                    "2;0.4050;0.2025;0.2025;0.5000\n" +
                    "3;0.3645;0.1823;0.1823;0.5000\n" +
                    "4;0.3281;0.1640;0.1640;0.5000\n" +
                    "5;0.2952;0.1476;0.1476;0.5000\n" +
                    "6;0.2657;0.1329;0.1329;0.5000\n" +
                    "7;0.2391;0.1196;0.1196;0.5000\n" +
                    "8;0.2152;0.1076;0.1076;0.5000\n" +
                    "9;0.1937;0.0969;0.0969;0.5000\n" +
                    "10;0.1743;0.0872;0.0872;0.5000\n";

    private String expectedExponentialAnneal =
            "it;globalInnovationRate;ReRoute;SubtourModeChoice;ChangeExpBeta\n" +
                    "0;0.5000;0.2500;0.2500;0.5000\n" +
                    "1;0.4094;0.2047;0.2047;0.5000\n" +
                    "2;0.3352;0.1676;0.1676;0.5000\n" +
                    "3;0.2744;0.1372;0.1372;0.5000\n" +
                    "4;0.2247;0.1123;0.1123;0.5000\n" +
                    "5;0.1839;0.0920;0.0920;0.5000\n" +
                    "6;0.1506;0.0753;0.0753;0.5000\n" +
                    "7;0.1233;0.0616;0.0616;0.5000\n" +
                    "8;0.1009;0.0505;0.0505;0.5000\n" +
                    "9;0.0826;0.0413;0.0413;0.5000\n" +
                    "10;0.0677;0.0338;0.0338;0.5000\n";

    private String expectedSigmoidAnneal =
            "it;globalInnovationRate;ReRoute;SubtourModeChoice;ChangeExpBeta\n" +
                    "0;0.5000;0.2500;0.2500;0.5000\n" +
                    "1;0.4910;0.2455;0.2455;0.5000\n" +
                    "2;0.4763;0.2381;0.2381;0.5000\n" +
                    "3;0.4404;0.2202;0.2202;0.5000\n" +
                    "4;0.3656;0.1828;0.1828;0.5000\n" +
                    "5;0.2501;0.1250;0.1250;0.5000\n" +
                    "6;0.1345;0.0673;0.0673;0.5000\n" +
                    "7;0.0597;0.0298;0.0298;0.5000\n" +
                    "8;0.0238;0.0119;0.0119;0.5000\n" +
                    "9;0.0091;0.0045;0.0045;0.5000\n" +
                    "10;0.0034;0.0017;0.0017;0.5000\n";

    private String expectedParameterAnneal =
            "it;BrainExpBeta\n" +
                    "0;1.0000\n" +
                    "1;0.9000\n" +
                    "2;0.8000\n" +
                    "3;0.7000\n" +
                    "4;0.6000\n" +
                    "5;0.5000\n" +
                    "6;0.4000\n" +
                    "7;0.3000\n" +
                    "8;0.2000\n" +
                    "9;0.1000\n" +
                    "10;0.0000\n";

    private String expectedTwoParameterAnneal =
            "it;globalInnovationRate;ReRoute;SubtourModeChoice;ChangeExpBeta;BrainExpBeta\n"+
                    "0;0.5000;0.2500;0.2500;0.5000;1.0000\n"+
                    "1;0.5000;0.2500;0.2500;0.5000;0.9000\n"+
                    "2;0.2500;0.1250;0.1250;0.5000;0.8000\n"+
                    "3;0.1667;0.0833;0.0833;0.5000;0.7000\n"+
                    "4;0.1250;0.0625;0.0625;0.5000;0.6000\n"+
                    "5;0.1000;0.0500;0.0500;0.5000;0.5000\n"+
                    "6;0.0833;0.0417;0.0417;0.5000;0.4000\n"+
                    "7;0.0714;0.0357;0.0357;0.5000;0.3000\n"+
                    "8;0.0625;0.0313;0.0313;0.5000;0.2000\n"+
                    "9;0.0556;0.0278;0.0278;0.5000;0.1000\n"+
                    "10;0.0500;0.0250;0.0250;0.5000;0.0000\n";

    private String expectedFreezeEarlyAnneal =
            "it;globalInnovationRate;ReRoute;SubtourModeChoice;ChangeExpBeta\n" +
                    "0;0.5000;0.2500;0.2500;0.5000\n" +
                    "1;0.5000;0.2500;0.2500;0.5000\n" +
                    "2;0.2500;0.1250;0.1250;0.5000\n" +
                    "3;0.1667;0.0833;0.0833;0.5000\n" +
                    "4;0.1250;0.0625;0.0625;0.5000\n" +
                    "5;0.1000;0.0500;0.0500;0.5000\n" +
                    "6;0.1000;0.0500;0.0500;0.5000\n" +
                    "7;0.1000;0.0500;0.0500;0.5000\n" +
                    "8;0.1000;0.0500;0.0500;0.5000\n" +
                    "9;0.1000;0.0500;0.0500;0.5000\n" +
                    "10;0.1000;0.0500;0.0500;0.5000\n";

    private String expectedInnovationSwitchoffAnneal =
            "it;globalInnovationRate;ReRoute;SubtourModeChoice;ChangeExpBeta\n" +
                    "0;0.5000;0.2500;0.2500;0.5000\n" +
                    "1;0.5000;0.2500;0.2500;0.5000\n" +
                    "2;0.2500;0.1250;0.1250;0.5000\n" +
                    "3;0.1667;0.0833;0.0833;0.5000\n" +
                    "4;0.1250;0.0625;0.0625;0.5000\n" +
                    "5;0.1000;0.0500;0.0500;0.5000\n" +
                    "6;0.0000;0.0000;0.0000;0.5000\n" +
                    "7;0.0000;0.0000;0.0000;0.5000\n" +
                    "8;0.0000;0.0000;0.0000;0.5000\n" +
                    "9;0.0000;0.0000;0.0000;0.5000\n" +
                    "10;0.0000;0.0000;0.0000;0.5000\n";

}
