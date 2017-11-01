/* *********************************************************************** *
 * project: org.matsim.*												   *
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
package ch.sbb.matsim;

import org.apache.log4j.Logger;
import org.junit.Assert;
import org.junit.Test;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * @author denism
 *
 */
public class RunSBBTest {

    @Test
    public final void testMain() {
        try {
            Path configPath = Paths.get("CNB_sample_small/config.xml");
            String[] args = {configPath.toString()};
            RunSBB.main(args);

        } catch (Exception ee) {
            Logger.getLogger(this.getClass()).fatal("there was an exception: \n" + ee);

            // if one catches an exception, then one needs to explicitly fail the test:
        }


    }

}
