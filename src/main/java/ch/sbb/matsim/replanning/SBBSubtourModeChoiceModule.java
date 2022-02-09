/* *********************************************************************** *
 * project: org.matsim.*
 * SubtourModeChoice.java
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2008 by the members listed in the COPYING,        *
 *                   LICENSE and WARRANTY file.                            *
 * email           : info at matsim dot org                                *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *   See also COPYING, LICENSE and WARRANTY file                           *
 *                                                                         *
 * *********************************************************************** */

package ch.sbb.matsim.replanning;

import org.matsim.core.config.groups.GlobalConfigGroup;
import org.matsim.core.config.groups.SubtourModeChoiceConfigGroup;
import org.matsim.core.gbl.MatsimRandom;
import org.matsim.core.population.algorithms.PermissibleModesCalculator;
import org.matsim.core.population.algorithms.PlanAlgorithm;
import org.matsim.core.replanning.modules.AbstractMultithreadedModule;
import org.matsim.core.router.TripStructureUtils;

/**
 * A variation of the standard SubtourModechoiceModule to use a custom version of the Random Leg Mode choice
 */
public class SBBSubtourModeChoiceModule extends AbstractMultithreadedModule {

	private final double probaForChangeSingleTripMode;
	private final PermissibleModesCalculator permissibleModesCalculator;
	private final String[] chainBasedModes;
	private final String[] modes;
	private org.matsim.core.replanning.modules.SubtourModeChoice.Behavior behavior = org.matsim.core.replanning.modules.SubtourModeChoice.Behavior.fromSpecifiedModesToSpecifiedModes;

	public SBBSubtourModeChoiceModule(GlobalConfigGroup globalConfigGroup,
									  SubtourModeChoiceConfigGroup subtourModeChoiceConfigGroup, PermissibleModesCalculator permissibleModesCalculator) {
		this(globalConfigGroup.getNumberOfThreads(),
				subtourModeChoiceConfigGroup.getModes(),
				subtourModeChoiceConfigGroup.getChainBasedModes(),
				subtourModeChoiceConfigGroup.getProbaForRandomSingleTripMode(),
				permissibleModesCalculator
		);
		this.setBehavior(subtourModeChoiceConfigGroup.getBehavior());
	}

	SBBSubtourModeChoiceModule(
			final int numberOfThreads,
			final String[] modes,
			final String[] chainBasedModes,
			double probaForChangeSingleTripMode,
			PermissibleModesCalculator permissibleModesCalculator) {
		super(numberOfThreads);
		this.modes = modes.clone();
		this.chainBasedModes = chainBasedModes.clone();
		this.permissibleModesCalculator = permissibleModesCalculator;
		this.probaForChangeSingleTripMode = probaForChangeSingleTripMode;
	}

	@Deprecated // only use when backwards compatibility is needed. kai, may'18
	public final void setBehavior(org.matsim.core.replanning.modules.SubtourModeChoice.Behavior behavior) {
		this.behavior = behavior;
	}

	protected String[] getModes() {
		return modes.clone();
	}

	@Override
	public PlanAlgorithm getPlanAlgoInstance() {

		final ChooseRandomLegModeForSubtourWithSpatialVariation chooseRandomLegMode =
				new ChooseRandomLegModeForSubtourWithSpatialVariation(
						TripStructureUtils.getRoutingModeIdentifier(),
						this.permissibleModesCalculator,
						this.modes,
						this.chainBasedModes,
						MatsimRandom.getLocalInstance(), behavior, probaForChangeSingleTripMode);
		return chooseRandomLegMode;
	}


}
