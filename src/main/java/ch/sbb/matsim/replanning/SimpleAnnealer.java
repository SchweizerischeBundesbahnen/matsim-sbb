package ch.sbb.matsim.replanning;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

import ch.sbb.matsim.csv.CSVWriter;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.StrategyConfigGroup;
import org.matsim.core.controler.events.ShutdownEvent;
import org.matsim.core.controler.events.StartupEvent;
import org.matsim.core.controler.listener.ShutdownListener;
import org.matsim.core.controler.listener.StartupListener;
import org.matsim.core.replanning.strategies.DefaultPlanStrategiesModule.DefaultSelector;
import org.matsim.core.controler.events.IterationStartsEvent;
import org.matsim.core.controler.listener.IterationStartsListener;
import org.matsim.core.replanning.GenericPlanStrategy;
import org.matsim.core.replanning.StrategyManager;
import static ch.sbb.matsim.replanning.SimpleAnnealerConfigGroup.annealParameterOption;
import static ch.sbb.matsim.replanning.SimpleAnnealerConfigGroup.annealOption;
import static ch.sbb.matsim.replanning.SimpleAnnealerConfigGroup.AnnealingVariable;

import javax.inject.Inject;

/**
 * @author fouriep, davig
 */

public class SimpleAnnealer implements IterationStartsListener, StartupListener, ShutdownListener {

    private EnumMap<annealParameterOption, Double> currentValues;
    private int currentIter;
    private static final Logger log = Logger.getLogger(SimpleAnnealer.class);
    private final Config config;
    private final SimpleAnnealerConfigGroup saConfig;
    private static final String ANNEAL_FILENAME = "annealingRates.csv";
    private static final String COL_IT = "it";
    private final int innovationStop;
    private CSVWriter writer;

    @Inject
    public SimpleAnnealer(Config config) {
        this.config = config;
        this.saConfig = ConfigUtils.addOrGetModule(config, SimpleAnnealerConfigGroup.class);
        this.currentValues = new EnumMap<>(annealParameterOption.class);
        this.innovationStop = getInnovationStop(config);
    }

    @Override
    public void notifyStartup(StartupEvent event) {
        List<String> header = new ArrayList<>();
        header.add(COL_IT);
        for (AnnealingVariable av : this.saConfig.getAnnealingVariables().values()) {
            if (!av.getAnnealType().equals(annealOption.disabled)) {
                // check and fix initial value if needed
                checkAndFixStartValue(av);

                this.currentValues.put(av.getAnnealParameter(), av.getStartValue());
                header.add(av.getAnnealParameter().name());
                if (av.getAnnealParameter().equals(annealParameterOption.globalInnovationRate)) {
                    header.addAll(this.config.strategy().getStrategySettings().stream()
                            .map(StrategyConfigGroup.StrategySettings::getStrategyName)
                            .collect(Collectors.toList()));
                }
            }
        }
        // prepare output file
        try {
            this.writer = new CSVWriter("", header.toArray(new String[0]),
                    event.getServices().getControlerIO().getOutputFilename(ANNEAL_FILENAME));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void notifyShutdown(ShutdownEvent event) {
        try {
            this.writer.close();
        } catch (IOException e) {
            log.warn("Couldn't close annealing rates file ", e);
        }
    }

    @Override
    public void notifyIterationStarts(IterationStartsEvent event) {
        this.currentIter = event.getIteration() - this.config.controler().getFirstIteration();
        this.writer.set(COL_IT, String.valueOf(this.currentIter));
        for (AnnealingVariable av : this.saConfig.getAnnealingVariables().values()) {
            if (this.currentIter > 0) {
                switch (av.getAnnealType()) {
                    case geometric:
                        this.currentValues.compute(av.getAnnealParameter(), (k,v) ->
                                v * av.getShapeFactor());
                        break;
                    case exponential:
                        this.currentValues.compute(av.getAnnealParameter(), (k,v) ->
                                av.getStartValue() / Math.exp((double) this.currentIter / av.getHalfLife()));
                        break;
                    case msa:
                        this.currentValues.compute(av.getAnnealParameter(), (k,v) ->
                                av.getStartValue() / Math.pow(this.currentIter, av.getShapeFactor()));
                        break;
                    case sigmoid:
                        this.currentValues.compute(av.getAnnealParameter(), (k,v) ->
                                av.getEndValue() + (av.getStartValue() - av.getEndValue()) /
                                        (1 + Math.exp(av.getShapeFactor()*(this.currentIter - av.getHalfLife()))));
                        break;
                    case linear:
                        double slope = (av.getStartValue() - av.getEndValue())
                                / (this.config.controler().getFirstIteration() - this.innovationStop);
                        this.currentValues.compute(av.getAnnealParameter(), (k,v) ->
                                this.currentIter * slope + av.getStartValue());
                        break;
                    case disabled:
                        return;
                    default:
                        throw new IllegalArgumentException();
                }

                log.info("Annealling will be performed on parameter " + av.getAnnealParameter() +
                        ". Value: " + this.currentValues.get(av.getAnnealParameter()));

                this.currentValues.compute(av.getAnnealParameter(), (k,v) ->
                        Math.max(v, av.getEndValue()));
            }
            double annealValue = this.currentValues.get(av.getAnnealParameter());
            this.writer.set(av.getAnnealParameter().name(), String.format("%.4f", annealValue));
            anneal(event, av, annealValue);
        }
        this.writer.writeRow();
    }

    private void anneal(IterationStartsEvent event, AnnealingVariable av, double annealValue) {
        switch (av.getAnnealParameter()) {
            case BrainExpBeta:
                this.config.planCalcScore().setBrainExpBeta(annealValue);
                break;
            case PathSizeLogitBeta:
                this.config.planCalcScore().setPathSizeLogitBeta(annealValue);
                break;
            case learningRate:
                this.config.planCalcScore().setLearningRate(annealValue);
                break;
            case SBBTimeMutatorReRoute: case TimeAllocationMutator: case SubtourModeChoice: case ReRoute:
                this.config.strategy().getStrategySettings().stream()
                        .filter(s -> av.getAnnealParameter().name().equals(s.getStrategyName()))
                        .collect(Collectors.toList()).get(0).setWeight(annealValue);
                break;
            case globalInnovationRate:
                List<Double> annealValues = annealReplanning(annealValue,
                        event.getServices().getStrategyManager(), av.getDefaultSubpopulation());
                int i = 0;
                for (StrategyConfigGroup.StrategySettings ss : this.config.strategy().getStrategySettings()) {
                    this.writer.set(ss.getStrategyName(), String.format("%.4f", annealValues.get(i)));
                    i++;
                }
                this.writer.set(av.getAnnealParameter().name(), String.format("%.4f", // update value in case of switchoff
                        getGlobalInnovationRate(event.getServices().getStrategyManager(), av.getDefaultSubpopulation())));
                break;
            default:
                throw new IllegalArgumentException();
        }
    }

    private List<Double> annealReplanning(double globalValue, StrategyManager stratMan, String subpopulation) {
        List<Double> annealValues = new ArrayList<>();
        double totalInnovationWeights = getGlobalInnovationRate(stratMan, subpopulation);
        List<GenericPlanStrategy<Plan, Person>> strategies = stratMan.getStrategies(subpopulation);
        for (GenericPlanStrategy<Plan, Person> strategy : strategies) {
            double weight = stratMan.getWeights(subpopulation).get(strategies.indexOf(strategy));
            if (isInnovationStrategy(strategy.toString())) {
                weight = totalInnovationWeights > 0 ?
                        globalValue * weight/totalInnovationWeights : 0.0;
                stratMan.changeWeightOfStrategy(strategy, subpopulation, weight);
            }
            annealValues.add(weight);
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

        return Math.min(innoStop, config.controler().getLastIteration());
    }

    private void checkAndFixStartValue(SimpleAnnealerConfigGroup.AnnealingVariable av) {
        Double configValue;
        switch (av.getAnnealParameter()) {
            case BrainExpBeta:
                configValue = this.config.planCalcScore().getBrainExpBeta();
                break;
            case PathSizeLogitBeta:
                configValue = this.config.planCalcScore().getPathSizeLogitBeta();
                break;
            case learningRate:
                configValue = this.config.planCalcScore().getLearningRate();
                break;
            case ReRoute: case SubtourModeChoice: case TimeAllocationMutator: case SBBTimeMutatorReRoute:
                configValue = this.config.strategy().getStrategySettings().stream()
                        .filter(s -> av.getAnnealParameter().name().equals(s.getStrategyName()))
                        .collect(Collectors.toList()).get(0).getWeight();
                break;
            case globalInnovationRate:
                configValue = getGlobalInnovationRate(this.config, av.getDefaultSubpopulation());
                break;
            default:
                throw new IllegalArgumentException();
        }
        if (!configValue.equals(av.getStartValue())) {
            log.warn("Anneal start value doesn't match config value. Resetting startValue to config value " + av.getAnnealParameter() + " of " + configValue);
            av.setStartValue(configValue);
        }
    }
}
