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

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.gbl.MatsimRandom;
import org.matsim.core.population.io.StreamingPopulationReader;
import org.matsim.core.population.io.StreamingPopulationWriter;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.io.IOUtils;

public class SlicePlansAccordingToRef {

    /*
     * extracts the same agents as in a reference case
     */
    public static void main(String[] args) {
        String inputPlansCase = args[0];
        String outputDirCase = args[1];
        Map<Id<Person>, Integer> personPartition = new HashMap<>();
        List<StreamingPopulationWriter> writersCase = new ArrayList<>();
        Random r = MatsimRandom.getRandom();

        int partitions = args.length - 2;
        for (int i = 2; i < args.length; i++) {
            Set<Id<Person>> personsPerPartition = readPersonsCSV(args[3]);
            int partition = i - 2;
            personsPerPartition.forEach(p -> personPartition.put(p, partition));

        }

        for (int i = 0; i < partitions; i++) {
            StreamingPopulationWriter writer = new StreamingPopulationWriter();
            writer.startStreaming(outputDirCase + "/population_" + i + ".xml.gz");
            writersCase.add(writer);
        }
        StreamingPopulationReader streamingPopulationReaderCase = new StreamingPopulationReader(ScenarioUtils.createScenario(ConfigUtils.createConfig()));
        streamingPopulationReaderCase.addAlgorithm(p -> {
            Integer part = personPartition.get(p.getId());
            if (part == null) {
                //random selection for newly added persons
                part = r.nextInt(partitions);
            }
            writersCase.get(part).run(p);
        });
        streamingPopulationReaderCase.readFile(inputPlansCase);

        writersCase.forEach(StreamingPopulationWriter::closeStreaming);

    }

    private static Set<Id<Person>> readPersonsCSV(String filename) {
        Set<Id<Person>> personIds = new HashSet<>();
        BufferedReader br = IOUtils.getBufferedReader(filename);
        try {
            String[] line = br.readLine().split(";");
            personIds.add(Id.createPersonId(line[0]));

        } catch (IOException e) {
            e.printStackTrace();
        }
        return personIds;
    }

}
