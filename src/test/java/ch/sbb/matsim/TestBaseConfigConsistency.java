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

package ch.sbb.matsim;

import org.junit.Test;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;

public class TestBaseConfigConsistency {

    @Test
    public void check2017Config() {
        System.setProperty("matsim.preferLocalDtds", "true");
        Config config = ConfigUtils.loadConfig("baseconfigs/config_2017_2030.xml", RunSBB.sbbDefaultConfigGroups);
        config.checkConsistency();
    }

    @Test
    public void check2040Config() {
        System.setProperty("matsim.preferLocalDtds", "true");
        Config config = ConfigUtils.loadConfig("baseconfigs/config_2040_2050.xml", RunSBB.sbbDefaultConfigGroups);
        config.checkConsistency();
    }

}
