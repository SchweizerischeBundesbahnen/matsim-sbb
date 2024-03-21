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

package ch.sbb.matsim.preparation;

import ch.sbb.matsim.preparation.slicer.PopulationSlicer;

import java.io.IOException;
import java.util.Set;

public class MergeAndSlice {

    public static void main(String[] args) {
        Set<String> folders = Set.of("C:\\devsbb\\fr2040");
        Set<Integer> toCut = Set.of(10, 4, 2);

        for (var s : folders) {
            //SimplePlansMerger.main(new String[]{s});
            toCut.parallelStream().forEach(d ->
                    {
                        try {
                            PopulationSlicer.
                                    main(new String[]{s + "\\plans.xml.gz", "-", Integer.toString(d)});
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
            );

        }
    }
}
