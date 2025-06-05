package ch.sbb.matsim.mavi.pt;

import ch.sbb.matsim.mavi.visum.VisumDataSource;
import ch.sbb.matsim.mavi.visum.VisumDataSource.DataTable;
import java.util.HashSet;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.utils.collections.Tuple;
import org.matsim.pt.transitSchedule.api.MinimalTransferTimes;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class MTTExporter {

	private static final Logger log = LogManager.getLogger(MTTExporter.class);

	private final TransitSchedule schedule;

	public MTTExporter(Scenario scenario) {
		this.schedule = scenario.getTransitSchedule();
	}

	private static List<Transfer> loadWithinTransfers(VisumDataSource visum) {
		log.info("loading within stop transfers");
		DataTable walkTimesTable = visum.getTable("TransferWalkTimeStopAreas");
		String[][] a = walkTimesTable.attrs("FromStopAreaNo", "ToStopAreaNo", "Time");

		int nrTransfers = walkTimesTable.size();
		log.info("loading " + nrTransfers + " within stop transfers");

		List<Transfer> transfers = IntStream.range(0, nrTransfers).
			filter(i -> parseSeconds(a[i][2]) > 0).
			mapToObj(i -> new Transfer((int) Double.parseDouble(a[i][0]),
				(int) Double.parseDouble(a[i][1]), Double.parseDouble(a[i][2]))).
			collect(Collectors.toList());

		log.info("finished loading " + transfers.size() + " valid within stop transfers");
		return transfers;
	}

	private static List<Transfer> loadBetweenTransfers(VisumDataSource visum) {
		DataTable stopAreas = visum.getTable("StopAreas");
		Map<String, List<String>> stopAreaNodeMap = stopAreas.buildMultiMap("NodeNo", "No");
		DataTable links = visum.getTable("Links");
		String[][] linkAttributes = links.attrs("FromNodeNo", "ToNodeNo", "T_PUTSYS(F)", "TSysSet");
		Map<Tuple<String, String>, String> walkLinksMap = IntStream.range(0, linkAttributes.length)
			.boxed()
			.filter(i -> linkAttributes[i][3].contains("F") || linkAttributes[i][3].contains("O"))
			.collect(Collectors.toMap(
				i -> new Tuple<>(linkAttributes[i][0], linkAttributes[i][1]),
				i -> linkAttributes[i][2],
				(existing, replacement) -> existing)
			);

		int nrOfLinks = walkLinksMap.size();
		log.info("loading " + nrOfLinks + " \"Fusswege\"");

		List<Transfer> transfers = new ArrayList<>();
		for (Tuple<String, String> linkNodeTuple : walkLinksMap.keySet()) {
			Set<String> fromStopAreas = new HashSet<>(stopAreaNodeMap.get(linkNodeTuple.getFirst()));
			Set<String> toStopAreas = new HashSet<>(stopAreaNodeMap.get(linkNodeTuple.getSecond()));
			double walkTime = parseSeconds(walkLinksMap.get(linkNodeTuple));
			if (walkTime > 0 && !fromStopAreas.isEmpty() && !toStopAreas.isEmpty()) {
				for (String fromStopArea : fromStopAreas) {
					for (String toStopArea : toStopAreas) {
						transfers.add(new Transfer((int) Double.parseDouble(fromStopArea), (int) Double.parseDouble(toStopArea),
							walkTime));
					}
				}
			}
		}

		log.info("finished loading " + transfers.size() + " valid \"Fusswege\"");
		return transfers;
	}

	public void integrateMinTransferTimes(VisumDataSource visum, HashMap<Integer, Set<Id<TransitStopFacility>>> stopAreasToStopPoints) {
		List<Transfer> withinTransfers = loadWithinTransfers(visum);
		List<Transfer> betweenTransfers = loadBetweenTransfers(visum);
		log.info("started integrating the within stop transfers into the schedule");
		integrateTransfers(withinTransfers, stopAreasToStopPoints);
		log.info("started integrating the \"Fusswege\" into the schedule");
		integrateTransfers(betweenTransfers, stopAreasToStopPoints);
	}

	private void integrateTransfers(List<Transfer> transfers, HashMap<Integer, Set<Id<TransitStopFacility>>> stopAreasToStopPoints) {
		int countAreaTransfers = 0;
		int countStopTransfers = 0;
		MinimalTransferTimes mtt = this.schedule.getMinimalTransferTimes();
		for (Transfer transfer : transfers) {
			Set<Id<TransitStopFacility>> fromStopFacilities = stopAreasToStopPoints.get(transfer.fromStopArea);
			Set<Id<TransitStopFacility>> toStopFacilities = stopAreasToStopPoints.get(transfer.toStopArea);

			if (fromStopFacilities != null && toStopFacilities != null) {
				countAreaTransfers++;
				for (Id<TransitStopFacility> fromFacilityId : fromStopFacilities) {
					for (Id<TransitStopFacility> toFacilityId : toStopFacilities) {
						countStopTransfers++;
						double oldValue = mtt.set(fromFacilityId, toFacilityId, transfer.transferTime);
						if (!Double.isNaN(oldValue) && oldValue != transfer.transferTime) {
							log.warn("Overwrite transfer time from " + fromFacilityId + " to " + toFacilityId + ". oldValue = " + oldValue + "  newValue = " + transfer.transferTime);
						}
					}
				}
			}
		}
		log.info("used " + countAreaTransfers + " transfer relations between stop areas to generate " + countStopTransfers + " transfers between stop facilities");
	}

	private static double parseSeconds(String timeStr) {
		if (timeStr.endsWith("s")) timeStr = timeStr.substring(0, timeStr.length() - 1);
		return Double.parseDouble(timeStr);
	}

	private static class Transfer {

		final int fromStopArea;
		final int toStopArea;
		final double transferTime;

		public Transfer(int fromStopArea, int toStopArea, double transferTime) {
			this.fromStopArea = fromStopArea;
			this.toStopArea = toStopArea;
			this.transferTime = transferTime;
		}
	}
}