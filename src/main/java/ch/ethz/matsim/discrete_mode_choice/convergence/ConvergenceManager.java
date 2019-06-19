package ch.ethz.matsim.discrete_mode_choice.convergence;

import ch.ethz.matsim.discrete_mode_choice.convergence.criteria.ConvergenceCriterion;
import ch.ethz.matsim.discrete_mode_choice.convergence.variables.CategoricalVariable;
import ch.ethz.matsim.discrete_mode_choice.convergence.variables.ConvergenceVariable;
import ch.ethz.matsim.discrete_mode_choice.convergence.variables.DistributionVariable;
import ch.ethz.matsim.discrete_mode_choice.convergence.variables.PointVariable;
import ch.ethz.matsim.discrete_mode_choice.convergence.writer.*;
import org.matsim.core.controler.TerminationCriterion;
import org.matsim.core.controler.events.AfterMobsimEvent;
import org.matsim.core.controler.listener.AfterMobsimListener;

import java.io.File;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

public class ConvergenceManager implements TerminationCriterion, AfterMobsimListener {
	private final boolean writeOutput;
	private final File basePath;

	private final boolean isUsedAsTerminationCriterion;
	private Optional<ConvergenceCriterion> activeCriterion = Optional.empty();

	private final List<ConvergenceCriterion> criteria = new LinkedList<>();
	private final List<ConvergenceVariable> variables = new LinkedList<>();
	private final List<ConvergenceWriter> writers = new LinkedList<>();

	public ConvergenceManager(boolean isUsedAsTerminationCriterion, boolean writeOutput, File basePath) {
		this.isUsedAsTerminationCriterion = isUsedAsTerminationCriterion;
		this.writeOutput = writeOutput;
		this.basePath = basePath;
	}

	public void setActiveCriterion(ConvergenceCriterion activeCriterion) {
		this.activeCriterion = Optional.of(activeCriterion);
	}

	public void addCriterion(ConvergenceCriterion criterion) {
		this.criteria.add(criterion);

		if (writeOutput && criterion.getName() != null) {
			this.writers.add(new CriterionWriter(basePath, criterion));
		}
	}

	public void addVariable(ConvergenceVariable variable) {
		this.variables.add(variable);

		if (writeOutput && variable.getName() != null) {
			if (variable instanceof PointVariable) {
				this.writers.add(new PointWriter(basePath, (PointVariable) variable));
			}

			if (variable instanceof CategoricalVariable) {
				this.writers.add(new CategoricalWriter(basePath, (CategoricalVariable) variable));
			}

			if (variable instanceof DistributionVariable) {
				this.writers.add(new DistributionWriter(basePath, (DistributionVariable) variable));
			}
		}
	}

	@Override
	public boolean continueIterations(int iteration) {
		if (activeCriterion.isPresent()) {
			return !activeCriterion.get().isConverged();
		} else if (isUsedAsTerminationCriterion) {
			throw new IllegalStateException("No convergence criterion is active.");
		} else {
			return true;
		}
	}

	@Override
	public void notifyAfterMobsim(AfterMobsimEvent event) {
		for (ConvergenceVariable variable : variables) {
			variable.update(event.getIteration());
		}

		for (ConvergenceCriterion criterion : criteria) {
			criterion.update(event.getIteration());
		}

		for (ConvergenceWriter writer : writers) {
			try {
				writer.write(event.getIteration());
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}
	}
}
