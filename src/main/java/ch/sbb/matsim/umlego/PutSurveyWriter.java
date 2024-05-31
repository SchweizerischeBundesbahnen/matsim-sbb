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

import ch.sbb.matsim.csv.CSVWriter;
import ch.sbb.matsim.routing.pt.raptor.RaptorRoute;
import org.matsim.core.utils.misc.Time;
import org.matsim.pt.transitSchedule.api.TransitRoute;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

public class PutSurveyWriter implements AutoCloseable {

	private static final String COL_PATH_ID = "$OEVTEILWEG:DATENSATZNR";
	private static final String COL_LEG_ID = "TWEGIND";
	private static final String COL_FROM_STOP = "VONHSTNR";
	private static final String COL_TO_STOP = "NACHHSTNR";
	private static final String COL_VSYSCODE = "VSYSCODE";
	private static final String COL_LINNAME = "LINNAME";
	private static final String COL_LINROUTENAME = "LINROUTENAME";
	private static final String COL_RICHTUNGSCODE = "RICHTUNGSCODE";
	private static final String COL_FZPROFILNAME = "FZPNAME";
	private static final String COL_TEILWEG_KENNUNG = "TEILWEG-KENNUNG";
	private static final String COL_EINHSTNR = "EINHSTNR";
	private static final String COL_EINHSTABFAHRTSTAG = "EINHSTABFAHRTSTAG";
	private static final String COL_EINHSTABFAHRTSZEIT = "EINHSTABFAHRTSZEIT";
	private static final String COL_PFAHRT = "PFAHRT";
	private static final String COL_ORIG_GEM = "ORIG_GEM";
	private static final String COL_DEST_GEM = "DEST_GEM";
	private static final String COL_TOURID = "TOURID";
	private static final String COL_TRIPID = "TRIPID";
	private static final String COL_DIRECTION = "DIRECTION";
	private static final String[] COLUMNS = new String[]{COL_PATH_ID, COL_LEG_ID, COL_FROM_STOP, COL_TO_STOP, COL_VSYSCODE, COL_LINNAME, COL_LINROUTENAME, COL_RICHTUNGSCODE, COL_FZPROFILNAME,
			COL_TEILWEG_KENNUNG, COL_EINHSTNR, COL_EINHSTABFAHRTSTAG, COL_EINHSTABFAHRTSZEIT, COL_PFAHRT, COL_ORIG_GEM, COL_DEST_GEM,
			COL_TOURID, COL_TRIPID, COL_DIRECTION};

	private static final String HEADER = "$VISION\n* VisumInst\n* 10.11.06\n*\n*\n* Tabelle: Versionsblock\n$VERSION:VERSNR;FILETYPE;LANGUAGE;UNIT\n4.00;Att;DEU;KM\n*\n*\n* Tabelle: ÖV-Teilwege\n";

	public static final String STOP_NO = "02_Stop_No";
	public static final String TSYS_CODE = "09_TSysCode";
	public static final String DIRECTION_CODE = "04_DirectionCode";
	public static final String TRANSITLINE = "02_TransitLine";
	public static final String LINEROUTENAME = "03_LineRouteName";
	public static final String FZPNAME = "05_Name";
	private final CSVWriter writer;
	private final AtomicInteger teilwegNr = new AtomicInteger();

	public PutSurveyWriter(String filename) throws IOException {
		this.writer = new CSVWriter(HEADER, COLUMNS, filename);
	}

	public void writeRoute(String fromZone, String toZone, Umlego.FoundRoute route) {
		String pathId = String.valueOf(this.teilwegNr.incrementAndGet());
		int legId = 0;
		for (RaptorRoute.RoutePart routePart : route.routeParts) {
			if (routePart.line != null) {
				legId++;

				TransitRoute transitRoute = routePart.route;
				String fromStop = String.valueOf(routePart.fromStop.getAttributes().getAttribute(STOP_NO));
				String toStop = String.valueOf(routePart.toStop.getAttributes().getAttribute(STOP_NO));
				String vsyscode = String.valueOf(transitRoute.getAttributes().getAttribute(TSYS_CODE));
				String linname = String.valueOf(transitRoute.getAttributes().getAttribute(TRANSITLINE));
				String linroutename = String.valueOf(transitRoute.getAttributes().getAttribute(LINEROUTENAME));
				String richtungscode = String.valueOf(transitRoute.getAttributes().getAttribute(DIRECTION_CODE));

				String fzprofilname = String.valueOf(transitRoute.getAttributes().getAttribute(FZPNAME));

				String teilweg_kennung = legId > 1 ? "N" : "E";
				// always use day = 1
				String einhstabfahrtstag = "1";
//				String einhstabfahrtstag = getDayIndex(routePart.boardingTime);
				String einhstabfahrtszeit = getTime(routePart.boardingTime);

				String origGem = route.originStop.getName();
				String destGem = route.destinationStop.getName();

				writer.set(COL_PATH_ID, pathId);
				writer.set(COL_LEG_ID, Integer.toString(legId));
				writer.set(COL_FROM_STOP, fromStop);
				writer.set(COL_TO_STOP, toStop);
				writer.set(COL_VSYSCODE, vsyscode);
				writer.set(COL_LINNAME, linname);
				writer.set(COL_LINROUTENAME, linroutename);
				writer.set(COL_RICHTUNGSCODE, richtungscode);
				writer.set(COL_FZPROFILNAME, fzprofilname);
				writer.set(COL_TEILWEG_KENNUNG, teilweg_kennung);
				writer.set(COL_EINHSTNR, fromStop);
				writer.set(COL_EINHSTABFAHRTSTAG, einhstabfahrtstag);
				writer.set(COL_EINHSTABFAHRTSZEIT, einhstabfahrtszeit);
				writer.set(COL_PFAHRT, Double.toString(route.demand));
				writer.set(COL_ORIG_GEM, origGem);
				writer.set(COL_DEST_GEM, destGem);
				writer.set(COL_TOURID, "");
				writer.set(COL_TRIPID, "");
				writer.set(COL_DIRECTION, "");
				writer.writeRow();
			}
		}

	}

	@Override
	public void close() throws Exception {
		this.writer.close();
	}

	public static String getDayIndex(double time) {
		int day = (int) Math.ceil(time / (24 * 60 * 60.0));
		assert day > 0;
		return Integer.toString(day);
	}

	public static String getTime(double time) {
		double sec = time % (24 * 60 * 60);
		return Time.writeTime(sec);
	}
}
