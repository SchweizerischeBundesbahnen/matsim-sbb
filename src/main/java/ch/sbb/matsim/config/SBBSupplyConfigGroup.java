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

import java.net.URL;
import org.matsim.core.config.ConfigGroup;
import org.matsim.core.config.ReflectiveConfigGroup;

public class SBBSupplyConfigGroup extends ReflectiveConfigGroup {

    static public final String GROUP_NAME = "SBBSupply";
    private static final String PARAM_TRANSITNETWORK = "inputTransitNetworkFile";
    private static final String PARAM_CHECK_IF_PT_LINKS_EXIST = "checkIfTransitNetworkExistsAlready";
    private String transitNetworkFile = null;
    private boolean checkIfTransitNetworkExistsAlready = true;

    public SBBSupplyConfigGroup() {
        super(GROUP_NAME);
    }

    @StringGetter(PARAM_TRANSITNETWORK)
    public String getTransitNetworkFileString() {
        return transitNetworkFile;
    }

    public URL getTransitNetworkFile(URL context) {
        return ConfigGroup.getInputFileURL(context, transitNetworkFile);
    }

    @StringSetter(PARAM_TRANSITNETWORK)
    public void setTransitNetworkFile(String transitNetworkFile) {
        this.transitNetworkFile = transitNetworkFile;
    }

    @StringGetter(PARAM_CHECK_IF_PT_LINKS_EXIST)
    public boolean isCheckIfTransitNetworkExistsAlready() {
        return checkIfTransitNetworkExistsAlready;
    }

    @StringSetter(PARAM_CHECK_IF_PT_LINKS_EXIST)
    public void setCheckIfTransitNetworkExistsAlready(boolean checkIfTransitNetworkExistsAlready) {
        this.checkIfTransitNetworkExistsAlready = checkIfTransitNetworkExistsAlready;
    }

}
