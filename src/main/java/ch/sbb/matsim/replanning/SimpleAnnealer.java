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
    private static final String ANNEAL_FILENAME = "annealingRates.txt";
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
                checkAndFixStartValue(av, event);

                this.currentValues.put(av.getAnnealParameter(), av.getStartValue());
                header.add(av.getAnnealParameter().name());
                if (av.getAnnealParameter().equals(annealParameterOption.globalInnovationRate)) {
                    header.addAll(this.config.strategy().getStrategySettings().stream()
                            .filter(s -> Objects.equals(av.getDefaultSubpopulation(), s.getSubpopulation()))
                            .map(StrategyConfigGroup.StrategySettings::getStrategyName)
                            .collect(Collectors.toList()));
                }
            } else { // if disabled, better remove it
                this.saConfig.removeParameterSet(av);
            }
        }
        // prepare output file
        try {
            this.writer = new CSVWriter("", header.toArray(new String[0]),
                    event.getServices().getControlerIO().getOutputFilename(ANNEAL_FILENAME), "\t");
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
                        int halfLifeIter = av.getHalfLife() <= 1.0 ?
                                (int) (av.getHalfLife()*this.innovationStop) : (int) av.getHalfLife();
                        this.currentValues.compute(av.getAnnealParameter(), (k,v) ->
                                av.getStartValue() / Math.exp((double) this.currentIter / halfLifeIter));
                        break;
                    case msa:
                        this.currentValues.compute(av.getAnnealParameter(), (k,v) ->
                                av.getStartValue() / Math.pow(this.currentIter, av.getShapeFactor()));
                        break;
                    case sigmoid:
                        halfLifeIter = av.getHalfLife() <= 1.0 ?
                                (int) (av.getHalfLife()*this.innovationStop) : (int) av.getHalfLife();
                        this.currentValues.compute(av.getAnnealParameter(), (k,v) ->
                                av.getEndValue() + (av.getStartValue() - av.getEndValue()) /
                                        (1 + Math.exp(av.getShapeFactor()*(this.currentIter - halfLifeIter))));
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
        this.writer.writeRow(true);
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
                if (this.currentIter > this.innovationStop) { annealValue = 0.0; }
                List<Double> annealValues = annealReplanning(annealValue,
                        event.getServices().getStrategyManager(), av.getDefaultSubpopulation());
                int i = 0;
                for (StrategyConfigGroup.StrategySettings ss : this.config.strategy().getStrategySettings()) {
                    if (Objects.equals(ss.getSubpopulation(), av.getDefaultSubpopulation())) {
                        this.writer.set(ss.getStrategyName(), String.format("%.4f", annealValues.get(i)));
                        i++;
                    }
                }
                this.writer.set(av.getAnnealParameter().name(), String.format("%.4f", // update value in case of switchoff
                        getStrategyWeights(event.getServices().getStrategyManager(), av.getDefaultSubpopulation(), StratType.allInnovation)));
                break;
            default:
                throw new IllegalArgumentException();
        }
    }

    private List<Double> annealReplanning(double globalInnovationValue, StrategyManager stratMan, String subpopulation) {
        List<Double> annealValues = new ArrayList<>();
        double totalInnovationWeights = getStrategyWeights(stratMan, subpopulation, StratType.allInnovation);
        double totalSelectorWeights = getStrategyWeights(stratMan, subpopulation, StratType.allSelectors);
        List<GenericPlanStrategy<Plan, Person>> strategies = stratMan.getStrategies(subpopulation);
        for (GenericPlanStrategy<Plan, Person> strategy : strategies) {
            double weight = stratMan.getWeights(subpopulation).get(strategies.indexOf(strategy));
            if (isInnovationStrategy(strategy.toString())) {
                weight = totalInnovationWeights > 0 ?
                        globalInnovationValue * weight/totalInnovationWeights : 0.0;
            } else {
                weight = totalSelectorWeights > 0 ?
                        (1 - globalInnovationValue) * weight/totalSelectorWeights : 0.0000001;
            }
            stratMan.changeWeightOfStrategy(strategy, subpopulation, weight);
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

    private enum StratType {allInnovation, allSelectors, allStrategies}

    private double getStrategyWeights(StrategyManager stratMan, String subpopulation, StratType stratType) {
        if (this.currentIter == this.innovationStop + 1 && stratType.equals(StratType.allInnovation)) { return 0.0; }
        List<GenericPlanStrategy<Plan, Person>> strategies = stratMan.getStrategies(subpopulation);
        double totalWeights = 0.0;
        for (GenericPlanStrategy<Plan, Person> strategy : strategies) {
            double weight = stratMan.getWeights(subpopulation).get(strategies.indexOf(strategy));
            switch (stratType) {
                case allSelectors:
                    if (!isInnovationStrategy(strategy.toString())) { totalWeights += weight; }
                    break;
                case allInnovation:
                    if (isInnovationStrategy(strategy.toString())) { totalWeights += weight; }
                    break;
                case allStrategies:
                    totalWeights += weight;
                    break;
                default:
                    break;
            }
        }
        return totalWeights;
    }

    private double getStrategyWeights(Config config, String subpopulation, StratType stratType) {
        if (this.currentIter == this.innovationStop + 1 && stratType.equals(StratType.allInnovation)) { return 0.0; }
        Collection<StrategyConfigGroup.StrategySettings> strategies = config.strategy().getStrategySettings();
        double totalWeights = 0.0;
        for (StrategyConfigGroup.StrategySettings strategy : strategies) {
            if (Objects.equals(strategy.getSubpopulation(), subpopulation)) {
                switch (stratType) {
                    case allSelectors:
                        if (!isInnovationStrategy(strategy.toString())) { totalWeights += strategy.getWeight(); }
                        break;
                    case allInnovation:
                        if (isInnovationStrategy(strategy.toString())) { totalWeights += strategy.getWeight(); }
                        break;
                    case allStrategies:
                        totalWeights += strategy.getWeight();
                        break;
                    default:
                        break;
                }
            }
        }
        return totalWeights;
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

    private void checkAndFixStartValue(SimpleAnnealerConfigGroup.AnnealingVariable av, StartupEvent event) {
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
                double innovationWeights = getStrategyWeights(this.config, av.getDefaultSubpopulation(), StratType.allInnovation);
                double selectorWeights = getStrategyWeights(this.config, av.getDefaultSubpopulation(), StratType.allSelectors);
                if (innovationWeights + selectorWeights != 1.0) {
                    log.warn("Initial sum of strategy weights different from 1.0. Rescaling.");
                    double innovationStartValue = av.getStartValue() == null ? innovationWeights : av.getStartValue();
                    rescaleStartupWeights(innovationStartValue, this.config, event.getServices().getStrategyManager(), av.getDefaultSubpopulation());
                }
                configValue = getStrategyWeights(this.config, av.getDefaultSubpopulation(), StratType.allInnovation);
                break;
            default:
                throw new IllegalArgumentException();
        }
        if (av.getStartValue() == null) {
            log.warn("Anneal start value not set. Config value will be used.");
            av.setStartValue(configValue);
        }
    }

    private void rescaleStartupWeights(double innovationStartValue, Config config, StrategyManager stratMan, String subpopulation) {
        double selectorStartValue = 1 - innovationStartValue;
        // adapt simulation weights
        List<GenericPlanStrategy<Plan, Person>> strategies = stratMan.getStrategies(subpopulation);
        for (GenericPlanStrategy<Plan, Person> strategy : strategies) {
            double weight = stratMan.getWeights(subpopulation).get(strategies.indexOf(strategy));
            if (isInnovationStrategy(strategy.toString())) {
                weight = innovationStartValue > 0 ?
                        weight/innovationStartValue : 0.0;
            } else {
                weight = selectorStartValue > 0 ?
                        weight/selectorStartValue : 0.0;
            }
            stratMan.changeWeightOfStrategy(strategy, subpopulation, weight);
        }
        // adapt also in config for the record
        Collection<StrategyConfigGroup.StrategySettings> strategiesConfig = config.strategy().getStrategySettings();
        for (StrategyConfigGroup.StrategySettings strategy : strategiesConfig) {
            if (Objects.equals(strategy.getSubpopulation(), subpopulation)) {
                double weight = strategy.getWeight();
                if (isInnovationStrategy(strategy.toString())) {
                    weight = innovationStartValue > 0 ?
                            weight/innovationStartValue : 0.0;
                } else {
                    weight = selectorStartValue > 0 ?
                            weight/selectorStartValue : 0.0;
                }
                strategy.setWeight(weight);
            }
        }
    }
}
