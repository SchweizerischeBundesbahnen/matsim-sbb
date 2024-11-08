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
import ch.sbb.matsim.config.variables.Variables;
import ch.sbb.matsim.csv.CSVWriter;
import ch.sbb.matsim.mavi.MaviHelper;
import ch.sbb.matsim.mavi.streets.MergeRuralLinks;
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

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.GZIPOutputStream;

public class CarLinkAnalysis {

    static final String LINK_NO = "$LINK:NO";
    static final String FROMNODENO = "FROMNODENO";
    static final String TONODENO = "TONODENO";
    static final String LINK_ID_SIM = "LINK_ID_SIM";
	private static final String VOLUME_CAR = "VOLUME_CAR";
	private static final String VOLUME_FREIGHT = "VOLUME_FREIGHT";
	private static final String VOLUME_MOTORIZED_VEHICLES = "VOLUME_MOTORIZED_VEHICLES";

	private static final String VOLUME_RIDE = "VOLUME_RIDE";
	private static final String VOLUME_BIKE = "VOLUME_BIKE";
	private static final String VOLUME_BIKE_TOTAL = "VOLUME_BIKE_TOTAL";
	private static final String VOLUME_EBIKE = "VOLUME_EBIKE";
	private static final String[] VOLUMES_COLUMNS = new String[]{LINK_NO, FROMNODENO, TONODENO, LINK_ID_SIM, VOLUME_CAR, VOLUME_RIDE, VOLUME_BIKE, VOLUME_EBIKE, VOLUME_BIKE_TOTAL, VOLUME_FREIGHT, VOLUME_MOTORIZED_VEHICLES};
    static final String HEADER = "$VISION\n* Schweizerische Bundesbahnen SBB Personenverkehr Bern\n* 12/09/22\n* \n* Table: Version block\n* \n$VERSION:VERSNR;FILETYPE;LANGUAGE;UNIT\n12.00;Att;ENG;KM\n\n* \n* Table: Links\n* \n";
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
		try (OutputStream os = new GZIPOutputStream(new FileOutputStream(filename, true))) {
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
					double carVolume = volume.getCarCount() / samplesize;
					double freightVolume = volume.getFreightCount() / samplesize;
					Tuple<Integer, Integer> visumLinkNodeIds = MaviHelper.extractVisumNodeAndLinkId(link.getId());
					final String fromNode = link.getFromNode().getId().toString().startsWith("C_") ? link.getFromNode().getId().toString().substring(2) : link.getFromNode().getId().toString();
					final String toNode = link.getToNode().getId().toString().startsWith("C_") ? link.getToNode().getId().toString().substring(2) : link.getToNode().getId().toString();
					String id = link.getId().toString();
					String vLinks = (String) link.getAttributes().getAttribute(MergeRuralLinks.vlinks);
					double bikeCount = volume.getBikeCount() / samplesize;
					double ebikeCount = volume.getEbikeCount() / samplesize;
					double totalBikeCount = bikeCount + ebikeCount;
					if (vLinks != null) {
						String[] vlinksList = vLinks.split(",");
						List<VirtualVisumLink> virtualVisumLinkList = new ArrayList<>();
						int previousFromNode = -1;
						int previousLinkNo = -1;
						for (String vlink : vlinksList) {
							Tuple<Integer, Integer> virtualVisumLink = MaviHelper.extractVisumNodeAndLinkId(vlink);
							int currentFromNode = virtualVisumLink.getFirst();
							int currentLinkNo = virtualVisumLink.getSecond();
							if (previousFromNode > -1) {
								virtualVisumLinkList.add(new VirtualVisumLink(previousFromNode, currentFromNode, previousLinkNo));
							}
							previousLinkNo = currentLinkNo;
							previousFromNode = currentFromNode;
						}
						virtualVisumLinkList.add(new VirtualVisumLink(previousFromNode, Integer.parseInt(toNode), previousLinkNo));
						for (var vLink : virtualVisumLinkList) {
							writer.set(LINK_NO, String.valueOf(vLink.visumLinkNo()));
							writer.set(FROMNODENO, String.valueOf(vLink.fromNode()));
							writer.set(TONODENO, String.valueOf(vLink.toNode()));
							setVolumes(writer, id, carVolume, volume, (int) bikeCount, (int) ebikeCount, (int) totalBikeCount, freightVolume);
							writer.writeRow();
						}

					} else if (visumLinkNodeIds != null) {
						String visumNo = String.valueOf(visumLinkNodeIds.getSecond());
						writer.set(LINK_NO, visumNo);
						writer.set(TONODENO, toNode);
						writer.set(FROMNODENO, fromNode);
						setVolumes(writer, id, carVolume, volume, (int) bikeCount, (int) ebikeCount, (int) totalBikeCount, freightVolume);
						writer.writeRow();
					}

				}
			}

		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private void setVolumes(CSVWriter writer, String id, double carVolume, LinkStorage volume, int bikeCount, int ebikeCount, int totalBikeCount, double freightVolume) {
		writer.set(LINK_ID_SIM, id);
		writer.set(VOLUME_CAR, Integer.toString((int) carVolume));
		writer.set(VOLUME_RIDE, Integer.toString((int) (volume.getRideCount() / samplesize)));
		writer.set(VOLUME_BIKE, Integer.toString(bikeCount));
		writer.set(VOLUME_EBIKE, Integer.toString(ebikeCount));
		writer.set(VOLUME_BIKE_TOTAL, Integer.toString(totalBikeCount));
		writer.set(VOLUME_FREIGHT, Integer.toString((int) (volume.getFreightCount() / samplesize)));
		writer.set(VOLUME_MOTORIZED_VEHICLES, Integer.toString((int) (carVolume + freightVolume)));
	}

	record VirtualVisumLink(int fromNode, int toNode, int visumLinkNo) {
	}

    private void calculateVolumesPerLinkForNonNetworkModes(Map<Id<Link>, LinkStorage> linkVolumes, String mode, IterationLinkAnalyzer.AnalysisVehicleType vehicleType) {
		for (var person : population.getPersons().values()) {
			var personVehicleType = vehicleType;
			if (mode.equals(SBBModes.BIKE)) {
				boolean hasEBike = String.valueOf(person.getAttributes().getAttribute(Variables.HAS_EBIKE_45)).equals(Variables.AVAIL_TRUE);
				if (hasEBike) {
					personVehicleType = AnalysisVehicleType.ebike;
				}
			}
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
							linkStorage.increase(personVehicleType);
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
		private int ebikeCount = 0;

		LinkStorage(Id<Link> linkId) {
			this.linkId = linkId;
		}

		public void increase(IterationLinkAnalyzer.AnalysisVehicleType vehicleType) {
			switch (vehicleType) {
				case freight -> freightCount++;
				case car -> carCount++;
				case ride -> rideCount++;
				case bike -> bikeCount++;
				case ebike -> ebikeCount++;
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

		public int getEbikeCount() {
			return ebikeCount;
		}
	}
}

