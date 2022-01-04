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

package ch.sbb.matsim.config;

import ch.sbb.matsim.RunSBB;
import org.junit.Ignore;
import org.junit.Test;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigGroup;
import org.matsim.core.config.ConfigUtils;

public class TestBaseConfigConsistency {

    @Ignore
    @Test
    public void check2017Config() {
        ConfigGroup[] groups = getSBBConfigGroups();
        Config config_2017 = ConfigUtils.loadConfig("baseconfigs/config_2017_2030.xml", groups);
        config_2017.checkConsistency();

    }

    /*
     * Accessing the array directly leads to annoying problems if run on the build server
     */
    public ConfigGroup[] getSBBConfigGroups() {
        ConfigGroup[] groups = new ConfigGroup[RunSBB.sbbDefaultConfigGroups.length];
        System.arraycopy(RunSBB.sbbDefaultConfigGroups, 0, groups, 0, RunSBB.sbbDefaultConfigGroups.length);
        return groups;
    }

    @Ignore
    @Test
    public void check2040Config() {
        ConfigGroup[] groups = getSBBConfigGroups();
        Config config_2040 = ConfigUtils.loadConfig("baseconfigs/config_2040_2050.xml", groups);
        config_2040.checkConsistency();

    }

}
