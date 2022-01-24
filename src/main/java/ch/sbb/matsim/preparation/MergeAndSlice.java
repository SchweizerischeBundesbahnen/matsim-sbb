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

import java.io.IOException;
import java.util.Set;

public class MergeAndSlice {

    public static void main(String[] args) throws IOException {
        Set<String> folders = Set.of("\\\\wsbbrz0283\\mobi\\40_Projekte\\20210407_Prognose_2050\\2050\\plans_exogeneous\\make_plans\\road\\Liechtenstein\\merge\\2050",
                "\\\\wsbbrz0283\\mobi\\40_Projekte\\20210407_Prognose_2050\\2050\\plans_exogeneous\\make_plans\\road\\Liechtenstein\\merge\\2030",
                "\\\\wsbbrz0283\\mobi\\40_Projekte\\20210407_Prognose_2050\\2050\\plans_exogeneous\\make_plans\\road\\Liechtenstein\\merge\\2040",
                "\\\\wsbbrz0283\\mobi\\40_Projekte\\20210407_Prognose_2050\\2050\\plans_exogeneous\\make_plans\\road\\Liechtenstein\\merge\\2017");
        Set<Integer> toCut = Set.of(10, 4, 2);

        for (var s : folders) {
            SimplePlansMerger.main(new String[]{s});
            for (var d : toCut) {
                PopulationSlicer.main(new String[]{s + "\\plans.xml.gz", "-", Integer.toString(d)});

            }
        }
    }
}
