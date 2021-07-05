/* *********************************************************************** *
 * project: org.matsim.*
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2020 by the members listed in the COPYING,        *
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

package ch.sbb.matsim.preparation.casestudies;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.apache.commons.lang3.mutable.MutableInt;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.gbl.MatsimRandom;
import org.matsim.core.population.io.StreamingPopulationReader;
import org.matsim.core.population.io.StreamingPopulationWriter;
import org.matsim.core.scenario.ScenarioUtils;

public class SlicePlansBruggen {

    public static void main(String[] args) {
        String inputPlansCase = args[0];
        String inputPlansRef = args[1];
        String outputDirCase = args[2];
        String outputDirRef = args[3];
        int partitions = Integer.parseInt(args[4]);
        Random random = MatsimRandom.getRandom();
        //first, read the case (more plans than ref)
        List<StreamingPopulationWriter> writersCase = new ArrayList<>();
        Map<Id<Person>, Integer> personPartition = new HashMap<>();
        for (int i = 0; i < partitions; i++) {
            StreamingPopulationWriter writer = new StreamingPopulationWriter();
            writer.startStreaming(outputDirCase + "/population_" + i + ".xml.gz");
            writersCase.add(writer);
        }
        StreamingPopulationReader streamingPopulationReaderCase = new StreamingPopulationReader(ScenarioUtils.createScenario(ConfigUtils.createConfig()));
        MutableInt ii = new MutableInt();
        streamingPopulationReaderCase.addAlgorithm(p -> {
            int r = ii.toInteger() % partitions;
            personPartition.put(p.getId(), r);
            writersCase.get(r).run(p);
            ii.increment();
        });
        streamingPopulationReaderCase.readFile(inputPlansCase);
        writersCase.forEach(w -> w.closeStreaming());

        //now read reference and put person *if existing* into same partition
        List<StreamingPopulationWriter> writersRef = new ArrayList<>();

        for (int i = 0; i < partitions; i++) {
            StreamingPopulationWriter writer = new StreamingPopulationWriter();
            writer.startStreaming(outputDirRef + "/population_" + i + ".xml.gz");
            writersRef.add(writer);
        }
        StreamingPopulationReader streamingPopulationReaderRef = new StreamingPopulationReader(ScenarioUtils.createScenario(ConfigUtils.createConfig()));
        streamingPopulationReaderRef.addAlgorithm(person -> {
            Integer r = personPartition.get(person.getId());
            if (r != null) {
                writersRef.get(r).run(person);
            } else {
                writersRef.get(random.nextInt(partitions)).run(person);
                System.out.println(person.getId() + " not part of case population");
            }
        });
        streamingPopulationReaderRef.readFile(inputPlansRef);
        writersRef.forEach(w -> w.closeStreaming());
    }

}
