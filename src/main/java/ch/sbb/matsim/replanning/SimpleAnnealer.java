package ch.sbb.matsim.replanning;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.StrategyConfigGroup;
import org.matsim.core.controler.events.StartupEvent;
import org.matsim.core.controler.listener.StartupListener;
import org.matsim.core.replanning.strategies.DefaultPlanStrategiesModule.DefaultSelector;
import org.matsim.core.controler.events.IterationStartsEvent;
import org.matsim.core.controler.listener.IterationStartsListener;
import org.matsim.core.replanning.GenericPlanStrategy;
import org.matsim.core.replanning.StrategyManager;

import javax.inject.Inject;

/**
 * @author fouriep, davig
 */

public class SimpleAnnealer implements IterationStartsListener, StartupListener {

    private Double currentValue;
    private int currentIter;
    private static Logger log = Logger.getLogger(SimpleAnnealer.class);
    private Config config;
    private SimpleAnnealerConfigGroup saConfig;
    private final SimpleAnnealerConfigGroup.annealOption annealType;
    private final SimpleAnnealerConfigGroup.annealParameterOption annealParameter;
    private static final String ANNEAL_FILENAME = "annealingRates.txt";
    private String filename;
    private int innovationStop;

    @Inject
    public SimpleAnnealer(Config config) {
        this.config = config;
        this.saConfig = ConfigUtils.addOrGetModule(config, SimpleAnnealerConfigGroup.class);
        this.annealType = this.saConfig.getAnnealType();
        this.annealParameter = this.saConfig.getAnnealParameter();
    }

    @Override
    public void notifyStartup(StartupEvent event) {
        if (!this.saConfig.getAnnealType().equals(SimpleAnnealerConfigGroup.annealOption.disabled)) {
            // fix final iteration
            if (this.saConfig.getIterationToFreezeAnnealingRates() >= this.config.controler().getLastIteration()) {
                this.saConfig.setIterationToFreezeAnnealingRates(this.config.controler().getLastIteration());
            }
            this.innovationStop = getInnovationStop(this.config);
            if (this.saConfig.getAnnealParameter().equals(SimpleAnnealerConfigGroup.annealParameterOption.globalInnovationRate)
                    && this.saConfig.getIterationToFreezeAnnealingRates() > this.innovationStop) {
                log.info("IterationToFreezeAnnealingRates set after globalInnovationStop. Resetting IterationToFreezeAnnealingRates to the same value...");
                this.saConfig.setIterationToFreezeAnnealingRates(this.innovationStop);
            }
            // check and fix initial value if needed
            Double configValue;
            String header = this.annealParameter.toString();
            switch (this.annealParameter) {
                case BrainExpBeta:
                    configValue = this.config.planCalcScore().getBrainExpBeta();
                    break;
                case PathSizeLogitBeta:
                    configValue = this.config.planCalcScore().getPathSizeLogitBeta();
                    break;
                case learningRate:
                    configValue = this.config.planCalcScore().getLearningRate();
                    break;
                case globalInnovationRate:
                    configValue = getGlobalInnovationRate(this.config, this.saConfig.getDefaultSubpopulation());
                    header = this.config.strategy().getStrategySettings().stream()
                            .map(StrategyConfigGroup.StrategySettings::getStrategyName)
                            .collect(Collectors.joining("\t"));
                    break;
                default:
                    throw new IllegalArgumentException();
            }
            if (!configValue.equals(this.saConfig.getStartValue())) {
                log.warn("Anneal start value doesn't match config value. Resetting startValue to config value " + this.annealParameter.toString() + " of " + configValue);
                this.saConfig.setStartValue(configValue);
            }

            this.currentValue = this.saConfig.getStartValue();

            // prepare output file
            this.filename = event.getServices().getControlerIO().getOutputFilename(ANNEAL_FILENAME);
            try (BufferedWriter bw = new BufferedWriter(new FileWriter(
                    new File(this.filename)))) {
                bw.write("it\t" + header);
                bw.newLine();
            } catch (IOException e) { log.warn(e); }
        }
    }

    @Override
    public void notifyIterationStarts(IterationStartsEvent event) {
        this.currentIter = event.getIteration() - this.config.controler().getFirstIteration();
        if (this.currentIter > 0 && this.currentIter <= this.saConfig.getIterationToFreezeAnnealingRates()) {
            switch (this.annealType) {
                case geometric:
                    this.currentValue *= this.saConfig.getShapeFactor();
                    break;
                case exponential:
                    this.currentValue =
                            this.saConfig.getStartValue() / Math.exp((double) this.currentIter / this.saConfig.getHalfLife());
                    break;
                case msa:
                    this.currentValue =
                            this.saConfig.getStartValue() / Math.pow(this.currentIter, this.saConfig.getShapeFactor());
                    break;
                case sigmoid:
                    this.currentValue =
                            this.saConfig.getEndValue() + (this.saConfig.getStartValue() - this.saConfig.getEndValue()) /
                                    (1 + Math.exp(this.saConfig.getShapeFactor()*(this.currentIter - this.saConfig.getHalfLife())));
                    break;
                case linear:
                    double slope = (this.saConfig.getStartValue() - this.saConfig.getEndValue())
                            / (this.config.controler().getFirstIteration() - this.saConfig.getIterationToFreezeAnnealingRates());
                    this.currentValue = this.currentIter * slope + this.saConfig.getStartValue();
                    break;
                case disabled:
                    return;
                default:
                    throw new IllegalArgumentException();
            }

            log.info("Annealling will be performed on parameter " + this.saConfig.getAnnealParameter().toString() + ". Value: " + this.currentValue +
                    (this.saConfig.getAnnealParameter().equals(SimpleAnnealerConfigGroup.annealParameterOption.globalInnovationRate) ?
                            (". Subpopulation name: " + this.saConfig.getDefaultSubpopulation()) : ""));

            this.currentValue = Math.max(this.currentValue, this.saConfig.getEndValue());

            anneal(event, this.saConfig.getAnnealParameter());
        }

        List<Double> values = this.saConfig.getAnnealParameter().equals(SimpleAnnealerConfigGroup.annealParameterOption.globalInnovationRate) ?
                annealReplanning(this.currentValue, event.getServices().getStrategyManager(), this.saConfig.getDefaultSubpopulation(), true) :
                Collections.singletonList(this.currentValue);

        writeAnnealingRates(this.filename, event.getIteration(), values);
    }

    private void anneal(IterationStartsEvent event, SimpleAnnealerConfigGroup.annealParameterOption annealParameter) {
        switch (annealParameter) {
            case BrainExpBeta:
                this.config.planCalcScore().setBrainExpBeta(this.currentValue);
                break;
            case PathSizeLogitBeta:
                this.config.planCalcScore().setPathSizeLogitBeta(this.currentValue);
                break;
            case learningRate:
                this.config.planCalcScore().setLearningRate(this.currentValue);
                break;
            case globalInnovationRate:
                annealReplanning(this.currentValue, event.getServices().getStrategyManager(),
                        this.saConfig.getDefaultSubpopulation(), false);
                break;
            default:
                throw new IllegalArgumentException();
        }
    }

    private List<Double> annealReplanning(double globalValue, StrategyManager stratMan, String subpopulation, boolean coldRun) {
        List<Double> annealValues = new ArrayList<>();
        double totalInnovationWeights = getGlobalInnovationRate(stratMan, subpopulation);
        List<GenericPlanStrategy<Plan, Person>> strategies = stratMan.getStrategies(subpopulation);
        for (GenericPlanStrategy<Plan, Person> strategy : strategies) {
            double newWeight = totalInnovationWeights > 0 ? globalValue * stratMan.getWeights(subpopulation)
                    .get(strategies.indexOf(strategy))/totalInnovationWeights : 0.0;
            annealValues.add(newWeight);
            if(!coldRun && isInnovationStrategy(strategy.toString())) {
                stratMan.changeWeightOfStrategy(strategy, subpopulation, newWeight);
            }
        }
        return annealValues;
    }

    private static boolean isInnovationStrategy(String strategyName) {
        List<String> selectors = Arrays.asList(DefaultSelector.BestScore, DefaultSelector.ChangeExpBeta,
                DefaultSelector.KeepLastSelected, DefaultSelector.SelectExpBeta,
                DefaultSelector.SelectPathSizeLogit, DefaultSelector.SelectRandom, "selector", "expbeta");
        return !(selectors.contains(strategyName) ||
                ((strategyName.toLowerCase().contains("selector") || strategyName.toLowerCase().contains("expbeta")) && !strategyName.contains("_")));
    }

    private double getGlobalInnovationRate(StrategyManager stratMan, String subpopulation) {
        if (this.currentIter == this.innovationStop + 1) { return 0.0; }
        List<GenericPlanStrategy<Plan, Person>> strategies = stratMan.getStrategies(subpopulation);
        double totalInnovationWeights = 0.0;
        for (GenericPlanStrategy<Plan, Person> strategy : strategies) {
            if (isInnovationStrategy(strategy.toString())) {
                totalInnovationWeights += stratMan.getWeights(subpopulation).get(strategies.indexOf(strategy));
            }
        }
        return totalInnovationWeights;
    }

    private double getGlobalInnovationRate(Config config, String subpopulation) {
        if (this.currentIter == this.innovationStop + 1) { return 0.0; }
        Collection<StrategyConfigGroup.StrategySettings> strategies = config.strategy().getStrategySettings();
        double totalInnovationWeights = 0.0;
        for (StrategyConfigGroup.StrategySettings strategy : strategies) {
            if (isInnovationStrategy(strategy.getStrategyName()) && Objects.equals(strategy.getSubpopulation(), subpopulation)) {
                totalInnovationWeights += strategy.getWeight();
            }
        }
        return totalInnovationWeights;
    }

   private int getInnovationStop(Config config){
        int globalInnovationDisableAfter = (int) ((config.controler().getLastIteration() - config.controler().getFirstIteration())
                * config.strategy().getFractionOfIterationsToDisableInnovation() + config.controler().getFirstIteration());

        int innoStop = -1;

        for (StrategyConfigGroup.StrategySettings strategy : config.strategy().getStrategySettings()) {
            // check if this modules should be disabled after some iterations
            int maxIter = strategy.getDisableAfter();
            if ((maxIter > globalInnovationDisableAfter || maxIter==-1) && isInnovationStrategy(strategy.getStrategyName())) {
                maxIter = globalInnovationDisableAfter;
            }

            if(innoStop == -1) {
                innoStop = maxIter;
            }

            if(innoStop != maxIter){
                log.warn("Different 'Disable After Interation' values are set for different replaning modules." +
                        " Annealing doesn't support this function and will be performed according to the 'Disable After Interation' setting of the first replanning module " +
                        "or 'globalInnovationDisableAfter', which ever value is lower.");
            }
        }

        return innoStop;
    }

    private static void writeAnnealingRates(String filename, int iteration, List<Double> values) {
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(new File(filename), true))) {
            bw.write(iteration + "\t" + values.stream().map(o -> String.format("%.4f", o)).collect(Collectors.joining("\t")));
            bw.newLine();
        } catch (IOException e) { log.warn(e); }
    }
}
