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

package ch.sbb.matsim.analysis.linkAnalysis;

import ch.sbb.matsim.analysis.linkAnalysis.IterationLinkAnalyzer.AnalysisVehicleType;
import ch.sbb.matsim.config.PostProcessingConfigGroup;
import ch.sbb.matsim.config.variables.SBBModes;
import ch.sbb.matsim.csv.CSVWriter;
import ch.sbb.matsim.mavi.streets.MergeRuralLinks;
import ch.sbb.matsim.mavi.streets.VisumStreetNetworkExporter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Identifiable;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.Population;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.core.utils.collections.Tuple;
import org.matsim.core.utils.io.UncheckedIOException;

import java.io.*;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.zip.GZIPOutputStream;

public class CarLinkAnalysis {

	private static final String LINK_NO = "$LINK:NO";
	private static final String FROMNODENO = "FROMNODENO";
	private static final String TONODENO = "TONODENO";
	private static final String LINK_ID_SIM = "LINK_ID_SIM";
	private static final String VOLUME_CAR = "VOLUME_CAR";
	private static final String VOLUME_FREIGHT = "VOLUME_FREIGHT";
	private static final String VOLUME_RIDE = "VOLUME_RIDE";
	private static final String VOLUME_BIKE = "VOLUME_BIKE";
	private static final String[] VOLUMES_COLUMNS = new String[]{LINK_NO, FROMNODENO, TONODENO, LINK_ID_SIM, VOLUME_CAR, VOLUME_RIDE, VOLUME_BIKE, VOLUME_FREIGHT};
	private static final String HEADER = "$VISION\n* Schweizerische Bundesbahnen SBB Personenverkehr Bern\n* 12/09/22\n* \n* Table: Version block\n* \n$VERSION:VERSNR;FILETYPE;LANGUAGE;UNIT\n12.00;Att;ENG;KM\n\n* \n* Table: Links\n* \n";
	private final Network network;
	private final Population population;
	private final double samplesize;
	final IterationLinkAnalyzer linkAnalyzer;
	private boolean firstcall = true;
	private TreeSet<Id<Link>> carlinks;

	public CarLinkAnalysis(PostProcessingConfigGroup ppConfig, Scenario scenario, IterationLinkAnalyzer linkAnalyzer) {
		this.samplesize = ppConfig.getSimulationSampleSize();
		this.network = scenario.getNetwork();
		this.linkAnalyzer = linkAnalyzer;
		this.population = scenario.getPopulation();
	}

	public void writeMultiIterationCarStats(String filename, int iteration) {
		try (OutputStream os = new GZIPOutputStream(new FileOutputStream(new File(filename), true))) {
			BufferedWriter w = new BufferedWriter(new OutputStreamWriter(os));
			if (firstcall) {
				carlinks = network.getLinks().values()
						.stream()
						.filter(l -> l.getAllowedModes().contains(SBBModes.CAR))
						.map(Identifiable::getId)
						//.map(LinkStorage::new)
						.collect(Collectors.toCollection(TreeSet::new));
				w.write("Iteration;" + carlinks.stream().map(Objects::toString).collect(Collectors.joining(";")));
				firstcall = false;
			}
			var linkVolumes = linkAnalyzer.getIterationCounts();
			w.newLine();
			w.write(iteration);
			for (Id<Link> l : carlinks) {
				double vol = linkVolumes.getOrDefault(l, new LinkStorage(l)).getCarCount() + linkVolumes.getOrDefault(l, new LinkStorage(l)).getFreightCount() / samplesize;
				w.write(";");
				w.write(Integer.toString((int) vol));
			}

			w.flush();
		} catch (IOException e) {
			e.printStackTrace();
		}

	}

	public void writeSingleIterationStreetStats(String fileName) {
		var linkVolumes = linkAnalyzer.getIterationCounts();

		calculateVolumesPerLinkForNonNetworkModes(linkVolumes, SBBModes.RIDE, AnalysisVehicleType.ride);
		calculateVolumesPerLinkForNonNetworkModes(linkVolumes, SBBModes.BIKE, AnalysisVehicleType.bike);

		try (CSVWriter writer = new CSVWriter(HEADER, VOLUMES_COLUMNS, fileName)) {
			for (Map.Entry<Id<Link>, LinkStorage> entry : linkVolumes.entrySet()) {

				Link link = network.getLinks().get(entry.getKey());
				if (link != null) {
					var volume = entry.getValue();
					Tuple<Integer, Integer> visumLinkNodeIds = VisumStreetNetworkExporter.extractVisumNodeAndLinkId(link.getId());
					if (visumLinkNodeIds != null) {
						String visumNo = String.valueOf(visumLinkNodeIds.getSecond());
						writer.set(LINK_NO, visumNo);
						String id = link.getId().toString();
						writer.set(LINK_ID_SIM, id);
						String vnodes = (String) link.getAttributes().getAttribute(MergeRuralLinks.VNODES);
						final String fromNode = link.getFromNode().getId().toString().startsWith("C_") ? link.getFromNode().getId().toString().substring(2) : link.getFromNode().getId().toString();
						final String toNode = link.getToNode().getId().toString().startsWith("C_") ? link.getToNode().getId().toString().substring(2) : link.getToNode().getId().toString();

						if (vnodes != null) {
							String currentFromNode = fromNode;
							String[] nodes = vnodes.split(",");
							for (String node : nodes) {
								String currentToNode = node;
								currentToNode = currentToNode.startsWith("C_") ? currentToNode.substring(2) : currentToNode;
								writer.set(LINK_NO, visumNo);
								writer.set(FROMNODENO, currentFromNode);
								writer.set(TONODENO, currentToNode);
								writer.set(LINK_ID_SIM, id);
								writer.set(VOLUME_CAR, Integer.toString((int) (volume.getCarCount() / samplesize)));
								writer.set(VOLUME_RIDE, Integer.toString((int) (volume.getRideCount() / samplesize)));
								writer.set(VOLUME_BIKE, Integer.toString((int) (volume.getBikeCount() / samplesize)));
								writer.set(VOLUME_FREIGHT, Integer.toString((int) (volume.getFreightCount() / samplesize)));
								writer.writeRow();
								currentFromNode = currentToNode;

							}
							writer.set(LINK_NO, visumNo);
							writer.set(FROMNODENO, currentFromNode);
							writer.set(LINK_ID_SIM, id);

						} else {
							writer.set(LINK_NO, visumNo);
							writer.set(FROMNODENO, fromNode);
							writer.set(LINK_ID_SIM, id);
						}
						writer.set(TONODENO, toNode);
						writer.set(VOLUME_CAR, Integer.toString((int) (volume.getCarCount() / samplesize)));
						writer.set(VOLUME_RIDE, Integer.toString((int) (volume.getRideCount() / samplesize)));
						writer.set(VOLUME_BIKE, Integer.toString((int) (volume.getBikeCount() / samplesize)));
						writer.set(VOLUME_FREIGHT, Integer.toString((int) (volume.getFreightCount() / samplesize)));
						writer.writeRow();
					}
				}
			}

		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private void calculateVolumesPerLinkForNonNetworkModes(Map<Id<Link>, LinkStorage> linkVolumes, String mode, IterationLinkAnalyzer.AnalysisVehicleType vehicleType) {
		for (var person : population.getPersons().values()) {
			var plan = person.getSelectedPlan();
			var legs = TripStructureUtils.getLegs(plan);
			for (var leg : legs) {
				if (leg.getMode().equals(mode)) {
					var route = leg.getRoute();
					if (route.getRouteDescription() != null) {
						var linkIds = route.getRouteDescription().split(" ");
						for (var link : linkIds) {
							var linkId = Id.createLinkId(link);
							var linkStorage = linkVolumes.getOrDefault(linkId, new LinkStorage(linkId));
							linkStorage.increase(vehicleType);
							linkVolumes.put(linkId, linkStorage);
						}
					}
				}
			}
		}
	}

	static class LinkStorage {

		private final static Logger log = LogManager.getLogger(LinkStorage.class);

		private final Id<Link> linkId;

		private int freightCount = 0;
		private int carCount = 0;
		private int rideCount = 0;
		private int bikeCount = 0;

		LinkStorage(Id<Link> linkId) {
			this.linkId = linkId;
		}

		public void increase(IterationLinkAnalyzer.AnalysisVehicleType vehicleType) {
			switch (vehicleType) {
				case freight -> freightCount++;
				case car -> carCount++;
				case ride -> rideCount++;
				case bike -> bikeCount++;
				default -> log.warn("Vehicle type cannot be recognized");
			}
		}

		public Id<Link> getLinkId() {
			return linkId;
		}

		public int getFreightCount() {
			return freightCount;
		}

		public int getCarCount() {
			return carCount;
		}

		public int getRideCount() {
			return rideCount;
		}

		public int getBikeCount() {
			return bikeCount;
		}
	}
}

