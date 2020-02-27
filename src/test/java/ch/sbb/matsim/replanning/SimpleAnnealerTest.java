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
    private static final String FILENAME_ANNEAL = "annealingRates.txt";

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
        s1.setWeight(0.2);
        this.config.strategy().addStrategySettings(s1);
        StrategyConfigGroup.StrategySettings s2 = new StrategyConfigGroup.StrategySettings();
        s2.setStrategyName("SubtourModeChoice");
        s2.setWeight(0.2);
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
        this.saConfigVar.setStartValue(0.5);

        Controler controler = new Controler(this.scenario);
        controler.addOverridingModule(new AbstractModule() {
            @Override
            public void install() {addControlerListenerBinding().to(SimpleAnnealer.class);}});
        controler.run();

        Assert.assertEquals(expectedLinearAnneal, readResult(controler.getControlerIO().getOutputFilename(FILENAME_ANNEAL)));

        StrategyManager sm = controler.getInjector().getInstance(StrategyManager.class);
        List<Double> weights = sm.getWeights(null);

        Assert.assertEquals(1.0, weights.stream().mapToDouble(Double::doubleValue).sum(), 1e-4);
    }

    @Test
    public void testMsaAnneal() throws IOException {
        this.saConfigVar.setAnnealType("msa");
        this.saConfigVar.setShapeFactor(1.0);
        this.saConfigVar.setStartValue(0.5);

        Controler controler = new Controler(this.scenario);
        controler.addOverridingModule(new AbstractModule() {
            @Override
            public void install() {addControlerListenerBinding().to(SimpleAnnealer.class);}});
        controler.run();

        Assert.assertEquals(expectedMsaAnneal, readResult(controler.getControlerIO().getOutputFilename(FILENAME_ANNEAL)));

        StrategyManager sm = controler.getInjector().getInstance(StrategyManager.class);
        List<Double> weights = sm.getWeights(null);

        Assert.assertEquals(1.0, weights.stream().mapToDouble(Double::doubleValue).sum(), 1e-4);
    }

    @Test
    public void testGeometricAnneal() throws IOException {
        this.saConfigVar.setAnnealType("geometric");
        this.saConfigVar.setShapeFactor(0.9);
        this.saConfigVar.setStartValue(0.5);

        Controler controler = new Controler(this.scenario);
        controler.addOverridingModule(new AbstractModule() {
            @Override
            public void install() {addControlerListenerBinding().to(SimpleAnnealer.class);}});
        controler.run();

        Assert.assertEquals(expectedGeometricAnneal, readResult(controler.getControlerIO().getOutputFilename(FILENAME_ANNEAL)));

        StrategyManager sm = controler.getInjector().getInstance(StrategyManager.class);
        List<Double> weights = sm.getWeights(null);

        Assert.assertEquals(1.0, weights.stream().mapToDouble(Double::doubleValue).sum(), 1e-4);
    }

    @Test
    public void testExponentialAnneal() throws IOException {
        this.saConfigVar.setAnnealType("exponential");
        this.saConfigVar.setHalfLife(0.5);
        this.saConfigVar.setStartValue(0.5);

        Controler controler = new Controler(this.scenario);
        controler.addOverridingModule(new AbstractModule() {
            @Override
            public void install() {addControlerListenerBinding().to(SimpleAnnealer.class);}});
        controler.run();

        Assert.assertEquals(expectedExponentialAnneal, readResult(controler.getControlerIO().getOutputFilename(FILENAME_ANNEAL)));

        StrategyManager sm = controler.getInjector().getInstance(StrategyManager.class);
        List<Double> weights = sm.getWeights(null);

        Assert.assertEquals(1.0, weights.stream().mapToDouble(Double::doubleValue).sum(), 1e-4);
    }

    @Test
    public void testSigmoidAnneal() throws IOException {
        this.saConfigVar.setAnnealType("sigmoid");
        this.saConfigVar.setHalfLife(0.5);
        this.saConfigVar.setShapeFactor(1.0);
        this.saConfigVar.setStartValue(0.5);

        Controler controler = new Controler(this.scenario);
        controler.addOverridingModule(new AbstractModule() {
            @Override
            public void install() {addControlerListenerBinding().to(SimpleAnnealer.class);}});
        controler.run();

        Assert.assertEquals(expectedSigmoidAnneal, readResult(controler.getControlerIO().getOutputFilename(FILENAME_ANNEAL)));

        StrategyManager sm = controler.getInjector().getInstance(StrategyManager.class);
        List<Double> weights = sm.getWeights(null);

        Assert.assertEquals(1.0, weights.stream().mapToDouble(Double::doubleValue).sum(), 1e-4);
    }

    @Test
    public void testParameterAnneal() throws IOException {
        this.saConfigVar.setAnnealType("linear");
        this.saConfigVar.setAnnealParameter("BrainExpBeta");
        this.saConfigVar.setEndValue(0.0);
        this.saConfigVar.setStartValue(10.0);

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
        this.saConfigVar.setStartValue(0.5);

        SimpleAnnealerConfigGroup.AnnealingVariable otherAv = new SimpleAnnealerConfigGroup.AnnealingVariable();
        otherAv.setAnnealType("linear");
        otherAv.setEndValue(0.0);
        otherAv.setAnnealParameter("BrainExpBeta");
        otherAv.setStartValue(10.0);
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

        Assert.assertEquals(1.0, weights.stream().mapToDouble(Double::doubleValue).sum(), 1e-4);
    }

    @Test
    public void testInnovationSwitchoffAnneal() throws IOException {
        this.config.strategy().setFractionOfIterationsToDisableInnovation(0.5);
        this.saConfigVar.setAnnealType("msa");
        this.saConfigVar.setShapeFactor(1.0);
        this.saConfigVar.setStartValue(0.5);

        Controler controler = new Controler(this.scenario);
        controler.addOverridingModule(new AbstractModule() {
            @Override
            public void install() {addControlerListenerBinding().to(SimpleAnnealer.class);}});
        controler.run();

        Assert.assertEquals(expectedInnovationSwitchoffAnneal, readResult(controler.getControlerIO().getOutputFilename(FILENAME_ANNEAL)));

        StrategyManager sm = controler.getInjector().getInstance(StrategyManager.class);
        List<Double> weights = sm.getWeights(null);

        Assert.assertEquals(1.0, weights.stream().mapToDouble(Double::doubleValue).sum(), 1e-4);
    }

    @Test
    public void testFreezeEarlyAnneal() throws IOException {
        this.saConfigVar.setAnnealType("msa");
        this.saConfigVar.setShapeFactor(1.0);
        this.saConfigVar.setEndValue(0.1);
        this.saConfigVar.setStartValue(0.5);

        Controler controler = new Controler(this.scenario);
        controler.addOverridingModule(new AbstractModule() {
            @Override
            public void install() {addControlerListenerBinding().to(SimpleAnnealer.class);}});
        controler.run();

        Assert.assertEquals(expectedFreezeEarlyAnneal, readResult(controler.getControlerIO().getOutputFilename(FILENAME_ANNEAL)));

        StrategyManager sm = controler.getInjector().getInstance(StrategyManager.class);
        List<Double> weights = sm.getWeights(null);

        Assert.assertEquals(1.0, weights.stream().mapToDouble(Double::doubleValue).sum(), 1e-4);
    }

    @Test
    public void testSubpopulationAnneal() throws IOException {
        String targetSubpop = "subpop";
        this.saConfigVar.setAnnealType("linear");
        this.saConfigVar.setEndValue(0.0);
        this.saConfigVar.setStartValue(0.5);
        this.saConfigVar.setDefaultSubpopulation(targetSubpop);
        this.config.strategy().getStrategySettings().forEach(s -> s.setSubpopulation(targetSubpop));
        StrategyConfigGroup.StrategySettings s = new StrategyConfigGroup.StrategySettings();
        s.setStrategyName("TimeAllocationMutator");
        s.setWeight(0.25);
        s.setSubpopulation("noAnneal");
        this.config.strategy().addStrategySettings(s);

        Controler controler = new Controler(this.scenario);
        controler.addOverridingModule(new AbstractModule() {
            @Override
            public void install() {addControlerListenerBinding().to(SimpleAnnealer.class);}});
        controler.run();

        Assert.assertEquals(expectedLinearAnneal, readResult(controler.getControlerIO().getOutputFilename(FILENAME_ANNEAL)));

        StrategyManager sm = controler.getInjector().getInstance(StrategyManager.class);
        List<Double> weights = sm.getWeights(targetSubpop);

        Assert.assertEquals(1.0, weights.stream().mapToDouble(Double::doubleValue).sum(), 1e-4);
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
            "it\tglobalInnovationRate\tReRoute\tSubtourModeChoice\tChangeExpBeta\n" +
                    "0\t0.5000\t0.2500\t0.2500\t0.5000\n" +
                    "1\t0.4500\t0.2250\t0.2250\t0.5500\n" +
                    "2\t0.4000\t0.2000\t0.2000\t0.6000\n" +
                    "3\t0.3500\t0.1750\t0.1750\t0.6500\n" +
                    "4\t0.3000\t0.1500\t0.1500\t0.7000\n" +
                    "5\t0.2500\t0.1250\t0.1250\t0.7500\n" +
                    "6\t0.2000\t0.1000\t0.1000\t0.8000\n" +
                    "7\t0.1500\t0.0750\t0.0750\t0.8500\n" +
                    "8\t0.1000\t0.0500\t0.0500\t0.9000\n" +
                    "9\t0.0500\t0.0250\t0.0250\t0.9500\n" +
                    "10\t0.0000\t0.0000\t0.0000\t1.0000\n";

    private String expectedMsaAnneal =
            "it\tglobalInnovationRate\tReRoute\tSubtourModeChoice\tChangeExpBeta\n" +
                    "0\t0.5000\t0.2500\t0.2500\t0.5000\n" +
                    "1\t0.5000\t0.2500\t0.2500\t0.5000\n" +
                    "2\t0.2500\t0.1250\t0.1250\t0.7500\n" +
                    "3\t0.1667\t0.0833\t0.0833\t0.8333\n" +
                    "4\t0.1250\t0.0625\t0.0625\t0.8750\n" +
                    "5\t0.1000\t0.0500\t0.0500\t0.9000\n" +
                    "6\t0.0833\t0.0417\t0.0417\t0.9167\n" +
                    "7\t0.0714\t0.0357\t0.0357\t0.9286\n" +
                    "8\t0.0625\t0.0313\t0.0313\t0.9375\n" +
                    "9\t0.0556\t0.0278\t0.0278\t0.9444\n" +
                    "10\t0.0500\t0.0250\t0.0250\t0.9500\n";

    private String expectedGeometricAnneal =
            "it\tglobalInnovationRate\tReRoute\tSubtourModeChoice\tChangeExpBeta\n" +
                    "0\t0.5000\t0.2500\t0.2500\t0.5000\n" +
                    "1\t0.4500\t0.2250\t0.2250\t0.5500\n" +
                    "2\t0.4050\t0.2025\t0.2025\t0.5950\n" +
                    "3\t0.3645\t0.1823\t0.1823\t0.6355\n" +
                    "4\t0.3281\t0.1640\t0.1640\t0.6719\n" +
                    "5\t0.2952\t0.1476\t0.1476\t0.7048\n" +
                    "6\t0.2657\t0.1329\t0.1329\t0.7343\n" +
                    "7\t0.2391\t0.1196\t0.1196\t0.7609\n" +
                    "8\t0.2152\t0.1076\t0.1076\t0.7848\n" +
                    "9\t0.1937\t0.0969\t0.0969\t0.8063\n" +
                    "10\t0.1743\t0.0872\t0.0872\t0.8257\n";

    private String expectedExponentialAnneal =
            "it\tglobalInnovationRate\tReRoute\tSubtourModeChoice\tChangeExpBeta\n" +
                    "0\t0.5000\t0.2500\t0.2500\t0.5000\n" +
                    "1\t0.4094\t0.2047\t0.2047\t0.5906\n" +
                    "2\t0.3352\t0.1676\t0.1676\t0.6648\n" +
                    "3\t0.2744\t0.1372\t0.1372\t0.7256\n" +
                    "4\t0.2247\t0.1123\t0.1123\t0.7753\n" +
                    "5\t0.1839\t0.0920\t0.0920\t0.8161\n" +
                    "6\t0.1506\t0.0753\t0.0753\t0.8494\n" +
                    "7\t0.1233\t0.0616\t0.0616\t0.8767\n" +
                    "8\t0.1009\t0.0505\t0.0505\t0.8991\n" +
                    "9\t0.0826\t0.0413\t0.0413\t0.9174\n" +
                    "10\t0.0677\t0.0338\t0.0338\t0.9323\n";

    private String expectedSigmoidAnneal =
            "it\tglobalInnovationRate\tReRoute\tSubtourModeChoice\tChangeExpBeta\n" +
                    "0\t0.5000\t0.2500\t0.2500\t0.5000\n" +
                    "1\t0.4910\t0.2455\t0.2455\t0.5090\n" +
                    "2\t0.4763\t0.2381\t0.2381\t0.5237\n" +
                    "3\t0.4404\t0.2202\t0.2202\t0.5596\n" +
                    "4\t0.3656\t0.1828\t0.1828\t0.6344\n" +
                    "5\t0.2501\t0.1250\t0.1250\t0.7500\n" +
                    "6\t0.1345\t0.0673\t0.0673\t0.8655\n" +
                    "7\t0.0597\t0.0298\t0.0298\t0.9403\n" +
                    "8\t0.0238\t0.0119\t0.0119\t0.9762\n" +
                    "9\t0.0091\t0.0045\t0.0045\t0.9909\n" +
                    "10\t0.0034\t0.0017\t0.0017\t0.9966\n";

    private String expectedParameterAnneal =
            "it\tBrainExpBeta\n" +
                    "0\t10.0000\n" +
                    "1\t9.0000\n" +
                    "2\t8.0000\n" +
                    "3\t7.0000\n" +
                    "4\t6.0000\n" +
                    "5\t5.0000\n" +
                    "6\t4.0000\n" +
                    "7\t3.0000\n" +
                    "8\t2.0000\n" +
                    "9\t1.0000\n" +
                    "10\t0.0000\n";

    private String expectedTwoParameterAnneal =
            "it\tglobalInnovationRate\tReRoute\tSubtourModeChoice\tChangeExpBeta\tBrainExpBeta\n" +
                    "0\t0.5000\t0.2500\t0.2500\t0.5000\t10.0000\n" +
                    "1\t0.5000\t0.2500\t0.2500\t0.5000\t9.0000\n" +
                    "2\t0.2500\t0.1250\t0.1250\t0.7500\t8.0000\n" +
                    "3\t0.1667\t0.0833\t0.0833\t0.8333\t7.0000\n" +
                    "4\t0.1250\t0.0625\t0.0625\t0.8750\t6.0000\n" +
                    "5\t0.1000\t0.0500\t0.0500\t0.9000\t5.0000\n" +
                    "6\t0.0833\t0.0417\t0.0417\t0.9167\t4.0000\n" +
                    "7\t0.0714\t0.0357\t0.0357\t0.9286\t3.0000\n" +
                    "8\t0.0625\t0.0313\t0.0313\t0.9375\t2.0000\n" +
                    "9\t0.0556\t0.0278\t0.0278\t0.9444\t1.0000\n" +
                    "10\t0.0500\t0.0250\t0.0250\t0.9500\t0.0000\n";

    private String expectedFreezeEarlyAnneal =
            "it\tglobalInnovationRate\tReRoute\tSubtourModeChoice\tChangeExpBeta\n" +
                    "0\t0.5000\t0.2500\t0.2500\t0.5000\n" +
                    "1\t0.5000\t0.2500\t0.2500\t0.5000\n" +
                    "2\t0.2500\t0.1250\t0.1250\t0.7500\n" +
                    "3\t0.1667\t0.0833\t0.0833\t0.8333\n" +
                    "4\t0.1250\t0.0625\t0.0625\t0.8750\n" +
                    "5\t0.1000\t0.0500\t0.0500\t0.9000\n" +
                    "6\t0.1000\t0.0500\t0.0500\t0.9000\n" +
                    "7\t0.1000\t0.0500\t0.0500\t0.9000\n" +
                    "8\t0.1000\t0.0500\t0.0500\t0.9000\n" +
                    "9\t0.1000\t0.0500\t0.0500\t0.9000\n" +
                    "10\t0.1000\t0.0500\t0.0500\t0.9000\n";

    private String expectedInnovationSwitchoffAnneal =
            "it\tglobalInnovationRate\tReRoute\tSubtourModeChoice\tChangeExpBeta\n" +
                    "0\t0.5000\t0.2500\t0.2500\t0.5000\n" +
                    "1\t0.5000\t0.2500\t0.2500\t0.5000\n" +
                    "2\t0.2500\t0.1250\t0.1250\t0.7500\n" +
                    "3\t0.1667\t0.0833\t0.0833\t0.8333\n" +
                    "4\t0.1250\t0.0625\t0.0625\t0.8750\n" +
                    "5\t0.1000\t0.0500\t0.0500\t0.9000\n" +
                    "6\t0.0000\t0.0000\t0.0000\t1.0000\n" +
                    "7\t0.0000\t0.0000\t0.0000\t1.0000\n" +
                    "8\t0.0000\t0.0000\t0.0000\t1.0000\n" +
                    "9\t0.0000\t0.0000\t0.0000\t1.0000\n" +
                    "10\t0.0000\t0.0000\t0.0000\t1.0000\n";

}
