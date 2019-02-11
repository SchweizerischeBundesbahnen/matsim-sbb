/* *********************************************************************** *
 * project: org.matsim.*
 * TimeAllocationMutator.java
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2007 by the members listed in the COPYING,        *
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

import ch.sbb.matsim.config.variables.SBBActivities;
import org.matsim.core.config.groups.GlobalConfigGroup;
import org.matsim.core.config.groups.TimeAllocationMutatorConfigGroup;
import org.matsim.core.gbl.MatsimRandom;
import org.matsim.core.population.algorithms.PlanAlgorithm;
import org.matsim.core.replanning.modules.AbstractMultithreadedModule;


public class SBBTimeAllocationMutator extends AbstractMultithreadedModule {

    private final double mutationRange;

    public SBBTimeAllocationMutator(TimeAllocationMutatorConfigGroup timeAllocationMutatorConfigGroup, GlobalConfigGroup globalConfigGroup) {
        super(globalConfigGroup);
        this.mutationRange = timeAllocationMutatorConfigGroup.getMutationRange();
    }

    @Override
    public PlanAlgorithm getPlanAlgoInstance() {
        return new SBBTripPlanMutateTimeAllocation(SBBActivities.stageActivitiesTypes,
                this.mutationRange, MatsimRandom.getLocalInstance());
    }
}