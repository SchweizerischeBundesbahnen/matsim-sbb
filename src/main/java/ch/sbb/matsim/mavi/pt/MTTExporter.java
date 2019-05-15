package ch.sbb.matsim.mavi.pt;

import ch.sbb.matsim.mavi.visum.Visum;
import com.jacob.com.SafeArray;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.utils.collections.CollectionUtils;
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

    private static final Logger log = Logger.getLogger(MTTExporter.class);

    private final TransitSchedule schedule;

    public MTTExporter(Scenario scenario)   {
        this.schedule = scenario.getTransitSchedule();
    }

    public void integrateMinTransferTimes(Visum visum, HashMap<Integer, Set<Id<TransitStopFacility>>> stopAreasToStopPoints)  {
        List<Transfer> withinTransfers = loadWithinTransfers(visum);
        List<Transfer> betweenTransfers = loadBetweenTransfers(visum);
        log.info("started integrating the within stop transfers into the schedule");
        integrateTransfers(withinTransfers, stopAreasToStopPoints);
        log.info("started integrating the \"Fusswege\" into the schedule");
        integrateTransfers(betweenTransfers, stopAreasToStopPoints);
    }

    private static List<Transfer> loadWithinTransfers(Visum visum)  {
        log.info("loading within stop transfers");
        Visum.ComObject lists = visum.getComObject("Lists");
        Visum.ComObject walkTimes = visum.getComObject(lists, "CreateStopTransferWalkTimeList");
        walkTimes.callMethod("AddColumn", "FromStopAreaNo");
        walkTimes.callMethod("AddColumn", "ToStopAreaNo");
        walkTimes.callMethod("AddColumn", "Time(F)");
        SafeArray a = walkTimes.getSafeArray("SaveToArray");

        int nrTransfers = walkTimes.getNumActiveElements();
        log.info("loading " + nrTransfers + " within stop transfers");

        List<Transfer> transfers = IntStream.range(0, nrTransfers).
                filter(i -> Double.parseDouble(a.getString(i, 2)) > 0).
                mapToObj(i -> new Transfer((int) Double.parseDouble(a.getString(i, 0)),
                        (int) Double.parseDouble(a.getString(i, 1)), 60.0 * Double.parseDouble(a.getString(i, 2)))).
                collect(Collectors.toList());

        log.info("finished loading " + transfers.size() + " valid within stop transfers");
        return transfers;
    }

    private static List<Transfer> loadBetweenTransfers(Visum visum)  {
        Visum.ComObject filters = visum.getComObject("Filters");
        Visum.ComObject linkFilter = visum.callComObject(filters, "LinkFilter");
        visum.initFilter(linkFilter);
        visum.setFilterCondition(linkFilter, new Visum.FilterCondition(0, "OP_NONE", false, "TSysSet", 13, "F"));
        visum.useFilter(linkFilter, true);

        Visum.ComObject links = visum.getNetObject("Links");
        int nrOfLinks = links.countActive();
        log.info("loading " +  nrOfLinks + " \"Fusswege\"");

        String[][] linkAttributes = Visum.getArrayFromAttributeList(nrOfLinks, links,
                "FROMNODE\\DISTINCT:STOPAREAS\\NO", "TONODE\\DISTINCT:STOPAREAS\\NO", "T_PUTSYS(F)");

        List<Transfer> transfers = new ArrayList<>();
        IntStream.range(0, nrOfLinks).forEach(m -> addBetweenTransfersToList(m, transfers, linkAttributes));

        visum.useFilter(linkFilter, false);
        log.info("finished loading " + transfers.size() + " valid \"Fusswege\"");
        return transfers;
    }

    private static void addBetweenTransfersToList(int i, List<Transfer> transferList, String[][] a) {
        Set<String> fromStopAreas = CollectionUtils.stringToSet(a[i][0]);
        Set<String> toStopAreas = CollectionUtils.stringToSet(a[i][1]);
        double walkTime = Double.parseDouble(a[i][2]);
        if(walkTime > 0 && fromStopAreas.size() > 0 && toStopAreas.size() > 0) {
            for (String fromStopArea : fromStopAreas) {
                for (String toStopArea : toStopAreas) {
                    transferList.add(new Transfer((int) Double.parseDouble(fromStopArea), (int) Double.parseDouble(toStopArea),
                            walkTime));
                }
            }
        }
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