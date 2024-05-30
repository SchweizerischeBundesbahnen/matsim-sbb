/* *********************************************************************** *
 * project: org.matsim.*
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2024 by the members listed in the COPYING,        *
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

package ch.sbb.matsim.umlego;

import com.opencsv.CSVWriter;
import org.matsim.core.utils.io.IOUtils;
import org.matsim.core.utils.misc.Time;

public class UmlegoCsvWriter implements AutoCloseable {

	private final boolean writeDetails;
	private final CSVWriter writer;

	public UmlegoCsvWriter(String filename, boolean writeDetails) {
		this.writeDetails = writeDetails;
		this.writer = new CSVWriter(IOUtils.getBufferedWriter(filename), ',', '"', '\\', "\n");
		this.writer.writeNext(new String[]{"ORIGZONENO", "DESTZONENO", "ORIGNAME", "DESTNAME", "ACCESS_TIME", "EGRESS_TIME", "DEPTIME", "ARRTIME", "TRAVTIME", "NUMTRANSFERS", "DISTANZ", "DEMAND", "DETAILS"});
	}

	public void writeRoute(String origZone, String destZone, Umlego.FoundRoute route) {
		this.writer.writeNext(new String[]{
				origZone,
				destZone,
				route.originStop.getName(),
				route.destinationStop.getName(),
				Time.writeTime(route.originConnectedStop.walkTime()),
				Time.writeTime(route.destinationConnectedStop.walkTime()),
				Time.writeTime(route.depTime),
				Time.writeTime(route.arrTime),
				Time.writeTime(route.travelTimeWithoutAccess),
				Integer.toString(route.transfers),
				String.format("%.2f", route.distance / 1000.0),
				String.format("%.5f", route.demand),
				this.writeDetails ? route.getRouteAsString() : ""
		});
	}

	@Override
	public void close() throws Exception {
		this.writer.close();
	}
}
