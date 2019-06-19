package ch.ethz.matsim.discrete_mode_choice.convergence;

import ch.ethz.matsim.discrete_mode_choice.convergence.criteria.ConvergenceCriterion;
import ch.ethz.matsim.discrete_mode_choice.convergence.criteria.NeverCriterion;
import ch.ethz.matsim.discrete_mode_choice.convergence.variables.ConvergenceVariable;
import com.google.inject.Key;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.binder.LinkedBindingBuilder;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.controler.OutputDirectoryHierarchy;
import org.matsim.core.controler.TerminationCriterion;

import java.io.File;
import java.util.Set;

public class ConvergenceModule extends AbstractModule {
	public static final String ACTIVE_CONVERGENCE_CRITERION = "ActiveConvergenceCriterion";
	private boolean useAsTerminationCriterion = false;
	private boolean writeOutput = true;

	@Override
	public void install() {
		Multibinder.newSetBinder(binder(), ConvergenceVariable.class);
		Multibinder.newSetBinder(binder(), ConvergenceCriterion.class);

		addControlerListenerBinding().to(ConvergenceManager.class);

		if (useAsTerminationCriterion) {
			bind(TerminationCriterion.class).to(ConvergenceManager.class);
		}

		installCriteria();

		if (useAsTerminationCriterion) {
			bind(TerminationCriterion.class).to(ConvergenceManager.class);
		} else {
			bind(Key.get(ConvergenceCriterion.class, Names.named(ACTIVE_CONVERGENCE_CRITERION)))
					.toInstance(new NeverCriterion());
		}
	}

	public void installCriteria() {

	}

	protected LinkedBindingBuilder<ConvergenceVariable> bindVariable() {
		return Multibinder.newSetBinder(binder(), ConvergenceVariable.class).addBinding();
	}

	protected void addVariable(Key<? extends ConvergenceVariable> variableKey) {
		bindVariable().to(variableKey);
	}

	protected void addVariable(Class<? extends ConvergenceVariable> variableClass) {
		bindVariable().to(variableClass);
	}

	protected void addVariable(ConvergenceVariable variable) {
		bindVariable().toInstance(variable);
	}

	protected LinkedBindingBuilder<ConvergenceCriterion> bindCriterion() {
		return Multibinder.newSetBinder(binder(), ConvergenceCriterion.class).addBinding();
	}

	protected void addCriterion(Key<? extends ConvergenceCriterion> criterionKey) {
		bindCriterion().to(criterionKey);
	}

	protected void addCriterion(Class<? extends ConvergenceCriterion> criterionClass) {
		bindCriterion().to(criterionClass);
	}

	protected void addCriterion(ConvergenceCriterion criterion) {
		bindCriterion().toInstance(criterion);
	}

	protected void setActiveCriterion(Key<? extends ConvergenceCriterion> activeCriterionKey) {
		useAsTerminationCriterion = true;
		bind(Key.get(ConvergenceCriterion.class, Names.named(ACTIVE_CONVERGENCE_CRITERION))).to(activeCriterionKey);
	}

	protected void setActiveCriterion(Class<? extends ConvergenceCriterion> activeCriterionClass) {
		useAsTerminationCriterion = true;
		bind(Key.get(ConvergenceCriterion.class, Names.named(ACTIVE_CONVERGENCE_CRITERION))).to(activeCriterionClass);
	}

	protected void setActiveCriterion(ConvergenceCriterion activeCriterion) {
		useAsTerminationCriterion = true;
		bind(Key.get(ConvergenceCriterion.class, Names.named(ACTIVE_CONVERGENCE_CRITERION)))
				.toInstance(activeCriterion);
	}

	@Provides
	@Singleton
	public ConvergenceManager provideConvergenceManager(Set<ConvergenceVariable> variables,
			Set<ConvergenceCriterion> criteria,
			@Named(ACTIVE_CONVERGENCE_CRITERION) ConvergenceCriterion activeCriterion,
			OutputDirectoryHierarchy outputHierarchy) {
		File basePath = new File(outputHierarchy.getOutputFilename("convergence"));
		basePath.mkdir();

		ConvergenceManager manager = new ConvergenceManager(useAsTerminationCriterion, writeOutput, basePath);

		variables.forEach(manager::addVariable);
		criteria.forEach(manager::addCriterion);
		manager.setActiveCriterion(activeCriterion);

		return manager;
	}
}
