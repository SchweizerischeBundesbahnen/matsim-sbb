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
import org.junit.jupiter.api.Test;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;

public class TestBaseConfigConsistency {

    @Test
    public void check2017Config() {
        Config config_2017 = ConfigUtils.loadConfig("baseconfigs/config_2017.xml", RunSBB.getSbbDefaultConfigGroups());
        config_2017.checkConsistency();

    }

    @Test
    public void check2023Config() {
        Config config_2023 = ConfigUtils.loadConfig("baseconfigs/config_2023.xml", RunSBB.getSbbDefaultConfigGroups());
        config_2023.checkConsistency();

    }

    @Test
    public void check2030Config() {
        Config config_2030 = ConfigUtils.loadConfig("baseconfigs/config_2030.xml", RunSBB.getSbbDefaultConfigGroups());
        config_2030.checkConsistency();

    }

    @Test
    public void check2040Config() {
        Config config_2040 = ConfigUtils.loadConfig("baseconfigs/config_2040.xml", RunSBB.getSbbDefaultConfigGroups());
        config_2040.checkConsistency();

    }

    @Test
    public void check2050Config() {
        Config config_2050 = ConfigUtils.loadConfig("baseconfigs/config_2050.xml", RunSBB.getSbbDefaultConfigGroups());
        config_2050.checkConsistency();

    }

}
