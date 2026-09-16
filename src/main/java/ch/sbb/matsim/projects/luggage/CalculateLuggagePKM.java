package ch.sbb.matsim.projects.luggage;

import ch.sbb.matsim.csv.CSVReader;
import ch.sbb.matsim.csv.CSVWriter;
import ch.sbb.matsim.config.variables.SBBModes;
import ch.sbb.matsim.routing.pt.raptor.*;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Identifiable;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.core.router.DefaultRoutingRequest;
import org.matsim.core.router.LinkWrapperFacilityWithSpecificCoord;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.collections.Tuple;
import org.matsim.pt.routes.TransitPassengerRoute;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitScheduleReader;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class CalculateLuggagePKM {
  private static final String SEP = ";";
  private static final double DEPARTURE_TIME = 8 * 3600;
  private static final int MAX_COORDS_PER_ZIP = 10;
  private static final int MIN_PROGRESS_STEP = 250;

  // Fill this with known ZIP fallbacks, e.g. 3000 -> 3011
  private static final Map<String, String> ZIP_REPLACEMENTS = Map.ofEntries(
          Map.entry("3000", "3011"),
          Map.entry("4002", "4051"),
          Map.entry("7252", "7250"),
          Map.entry("7515", "7514"),
          Map.entry("7448", "7447"),
          Map.entry("8000", "8001"),
          Map.entry("6068", "6072"),
          Map.entry("8058", "8050"),
          Map.entry("1200", "1201"),
          Map.entry("7512", "7513"),
          Map.entry("4000", "4051"),
          Map.entry("9490", "9491"),
          Map.entry("7557", "7550"),
          Map.entry("6433", "6432"),
          Map.entry("6356", "6353"),
          Map.entry("7559", "7550"),
          Map.entry("5017", "5000"),
          Map.entry("3001", "3011"),
          Map.entry("7019", "7018"),
          Map.entry("3913", "3912"),
          Map.entry("7187", "7180"),
          Map.entry("8040", "8004"),
          Map.entry("9485", "9492"),
          Map.entry("7109", "7104"),
          Map.entry("7226", "7220"),
          Map.entry("5401", "5400"),
          Map.entry("9496", "9491"),
          Map.entry("7133", "7130"),
          Map.entry("6000", "6003"),
          Map.entry("1862", "1860"),
          Map.entry("6633", "6600"),
          Map.entry("6154", "6156"),
          Map.entry("8884", "8880"),
          Map.entry("7527", "7524"),
          Map.entry("4005", "4058"),
          Map.entry("3776", "3775"),
          Map.entry("3826", "3822"),
          Map.entry("3602", "3600"),
          Map.entry("7526", "7524"),
          Map.entry("6867", "6864"),
          Map.entry("9001", "9000"),
          Map.entry("8238", "8239"),
          Map.entry("9007", "9000"),
          Map.entry("1001", "1003"),
          Map.entry("8201", "8200"),
          Map.entry("8093", "8049"),
          Map.entry("6547", "6540"),
          Map.entry("8895", "8880"),
          Map.entry("6656", "6653"),
          Map.entry("1962", "1965"),
          Map.entry("3756", "3755"),
          Map.entry("6903", "6900"),
          Map.entry("3717", "3714"),
          Map.entry("7326", "7320"),
          Map.entry("7517", "7514"),
          Map.entry("6990", "6987"),
          Map.entry("8027", "8002"),
          Map.entry("1002", "1003"),
          Map.entry("1211", "1201"),
          Map.entry("5001", "5000"),   // Aarau Sammel → Aarau
          Map.entry("8060", "8050"),   // Zürich-Flughafen Sammel → Zürich Oerlikon
          Map.entry("2500", "2502"),   // Biel/Bienne Sammel → Biel/Bienne
          Map.entry("5402", "5400"),   // Baden Sammel → Baden
          Map.entry("1705", "1700"),   // Fribourg Sammel → Fribourg
          Map.entry("3825", "3822"),   // Mürren → Lauterbrunnen
          Map.entry("3980", "3979"),   // Bellwald/Goms-Sammel → Grengiols (bzw. 3984 Fiesch als Alternative)
          Map.entry("6002", "6003"),   // Luzern Sammel → Luzern
          Map.entry("23037", "7743"),  // Tirano (IT) → Poschiavo (letzter CH-Ort an der Bernina-Linie)
          Map.entry("8092", "8006")    // ETH Zentrum Zürich → Zürich


  );

  public static void main(String[] args) throws IOException {
    String scheduleFile = args.length > 0 ? args[0] : "D:\\mobi\\50_Ergebnisse\\MOBi_5.1\\pt\\NPVM2023\\output\\transitSchedule.xml.gz";
    String networkFile = args.length > 1 ? args[1] : "D:\\mobi\\50_Ergebnisse\\MOBi_5.1\\pt\\NPVM2023\\output\\transitNetwork.xml.gz";
    String relationsFile = args.length > 2 ? args[2] : "D:\\mobi\\40_Projekte\\20260904_PKM_Gepaeckverlad\\gemeindeliste_gepack.csv";
    String coordinatesWithZips = args.length > 3 ? args[3] : "D:\\mobi\\40_Projekte\\20260904_PKM_Gepaeckverlad\\zone_coordinates_mobi_with_zip.csv";
    String outputFile = args.length > 4 ? args[4] : "D:\\mobi\\40_Projekte\\20260904_PKM_Gepaeckverlad\\gemeindeliste_gepack_with_pkm.csv";
      int numberOfThreads = 12;


//    String scheduleFile = args.length > 0 ? args[0] : "/Volumes/mobi/50_Ergebnisse/MOBi_5.1/pt/NPVM2023/output/transitSchedule.xml.gz";
//    String networkFile = args.length > 1 ? args[1] : "/Volumes/mobi/50_Ergebnisse/MOBi_5.1/pt/NPVM2023/output/transitNetwork.xml.gz";
//    String relationsFile = args.length > 2 ? args[2] : "/Volumes/mobi/40_Projekte/20260904_PKM_Gepaeckverlad/gemeindeliste_gepack.csv";
//    String coordinatesWithZips = args.length > 3 ? args[3] : "/Volumes/mobi/40_Projekte/20260904_PKM_Gepaeckverlad/zone_coordinates_mobi_with_zip.csv";
//    String outputFile = args.length > 4 ? args[4] : "/Users/joschka.bischoff/tmp/gemeindeliste_gepack_with_pkm.csv";
//    int numberOfThreads = args.length > 5 ? Integer.parseInt(args[5]) : Math.max(1, Runtime.getRuntime().availableProcessors() - 1);
//

    Map<String, Coord> specialCases = Map.of(
        "Zürich Flughafen", new Coord(2684801, 1256124),
        "Genève Aéroport", new Coord(2497529, 1120854)
    );

    Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
    new TransitScheduleReader(scenario).readFile(scheduleFile);
    new MatsimNetworkReader(scenario.getNetwork()).readFile(networkFile);

    var railLines = scenario.getTransitSchedule().getTransitLines().values()
        .stream()
        .filter(l -> l.getRoutes().values().stream().anyMatch(transitRoute ->
            transitRoute.getTransportMode().equals(SBBModes.PTSubModes.RAIL)
                || transitRoute.getTransportMode().equals(SBBModes.PTRoutingModes.RAIL_ROUTE)))
        .map(Identifiable::getId)
        .collect(Collectors.toSet());

    scenario.getConfig().transitRouter().setMaxBeelineWalkConnectionDistance(300);
    SwissRailRaptorData data = SwissRailRaptorData.create(
        scenario.getTransitSchedule(),
        null,
        RaptorUtils.createStaticConfig(scenario.getConfig()),
        scenario.getNetwork(),
        null
    );
    ThreadLocal<SwissRailRaptor> raptorPerThread = ThreadLocal.withInitial(() -> createRaptor(scenario, data));

    Map<String, List<Coord>> coordsByZip = loadCoordinatesByZip(coordinatesWithZips);
    List<RelationRow> relationRows = new ArrayList<>();
    try (CSVReader relationsReader = new CSVReader(relationsFile, SEP)) {
      Map<String, String> relationRow;
      while ((relationRow = relationsReader.readLine()) != null) {
        String fromZip = normalizeZip(relationRow.get("from_zip"));
        String toZip = normalizeZip(relationRow.get("to_zip"));
        String fromMun = relationRow.get("from_mun");
        String toMun = relationRow.get("to_mun");
        relationRows.add(new RelationRow(fromZip, fromMun, toZip, toMun));
      }
    }

    printMissingZips(relationRows, coordsByZip);

    Set<RelationKey> uniqueRelationKeys = new LinkedHashSet<>();
    for (RelationRow row : relationRows) {
      uniqueRelationKeys.add(new RelationKey(
          replaceZip(row.fromZip()),
          row.fromMun(),
          replaceZip(row.toZip()),
          row.toMun()
      ));
    }
    int totalRows = relationRows.size();
    int uniqueRelations = uniqueRelationKeys.size();
    int duplicateRelations = totalRows - uniqueRelations;
    int computeProgressStep = Math.max(MIN_PROGRESS_STEP, uniqueRelations / 100);
    int writeProgressStep = Math.max(MIN_PROGRESS_STEP, totalRows / 100);

    System.out.printf(Locale.ROOT,
        "Relations: total=%d, unique=%d, duplicates=%d, threads=%d%n",
        totalRows,
        uniqueRelations,
        duplicateRelations,
        numberOfThreads
    );

    Map<RelationKey, Tuple<Double, Double>> relationPkmCache = new ConcurrentHashMap<>();
    ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
    try {
      AtomicInteger completed = new AtomicInteger(0);
      List<Future<?>> futures = new ArrayList<>();
      for (RelationKey relationKey : uniqueRelationKeys) {
        futures.add(executor.submit(() -> relationPkmCache.computeIfAbsent(relationKey, ignored -> {
          List<Coord> fromCoords = resolveCoords(relationKey.fromZip(), relationKey.fromMun(), coordsByZip, specialCases);
          List<Coord> toCoords = resolveCoords(relationKey.toZip(), relationKey.toMun(), coordsByZip, specialCases);
          Tuple<Double, Double> pkm = calculateAveragedPkm(
              raptorPerThread.get(),
              scenario,
              fromCoords,
              toCoords,
              railLines
          );
          int done = completed.incrementAndGet();
          if (done % computeProgressStep == 0 || done == uniqueRelations) {
            System.out.printf(Locale.ROOT, "Computed unique relations: %d/%d (%.1f%%)%n", done, uniqueRelations, (100.0 * done) / uniqueRelations);
          }
          return pkm;
        })));
      }
      for (Future<?> future : futures) {
        future.get();
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException("Interrupted while computing relation PKM cache", e);
    } catch (ExecutionException e) {
      throw new RuntimeException("Error while computing relation PKM cache", e);
    } finally {
      executor.shutdown();
    }

    try (CSVWriter writer = new CSVWriter(
            "\uFEFF",
        new String[]{"from_zip", "from_mun", "to_zip", "to_mun", "rail_pkm", "non_rail_pkm"},
        outputFile,
        SEP
    )) {
      int written = 0;
      for (RelationRow row : relationRows) {
        RelationKey relationKey = new RelationKey(
            replaceZip(row.fromZip()),
            row.fromMun(),
            replaceZip(row.toZip()),
            row.toMun()
        );
        Tuple<Double, Double> avgPkm = relationPkmCache.get(relationKey);
        if (avgPkm == null) {
          avgPkm = new Tuple<>(Double.NaN, Double.NaN);
        }

        writer.set("from_zip", row.fromZip());
        writer.set("from_mun", row.fromMun());
        writer.set("to_zip", row.toZip());
        writer.set("to_mun", row.toMun());
        writer.set("rail_pkm", formatDouble(avgPkm.getFirst()));
        writer.set("non_rail_pkm", formatDouble(avgPkm.getSecond()));
        writer.writeRow();

        written++;
        if (written % writeProgressStep == 0 || written == totalRows) {
          System.out.printf(Locale.ROOT, "Wrote output rows: %d/%d (%.1f%%)%n", written, totalRows, (100.0 * written) / totalRows);
        }
      }
    }
  }

  private static SwissRailRaptor createRaptor(Scenario scenario, SwissRailRaptorData data) {
    return new SwissRailRaptor(
        data,
        new DefaultRaptorParametersForPerson(scenario.getConfig()),
        null,
        new DefaultRaptorStopFinder(ConfigUtils.createConfig(), null, null),
        new DefaultRaptorInVehicleCostCalculator(),
        new DefaultRaptorTransferCostCalculator()
    );
  }

  private static void printMissingZips(List<RelationRow> relationRows, Map<String, List<Coord>> coordsByZip) {
    Set<String> missing = new LinkedHashSet<>();
    for (RelationRow row : relationRows) {
      String fromZip = replaceZip(row.fromZip());
      String toZip = replaceZip(row.toZip());
      if (!fromZip.isBlank() && !coordsByZip.containsKey(fromZip)) {
        missing.add(fromZip);
      }
      if (!toZip.isBlank() && !coordsByZip.containsKey(toZip)) {
        missing.add(toZip);
      }
    }

    if (missing.isEmpty()) {
      System.out.println("All relation ZIP codes are covered by coordinatesWithZips (after replacements).");
      return;
    }

    System.out.printf(Locale.ROOT, "Missing ZIP codes in coordinatesWithZips: %d%n", missing.size());
    missing.forEach(zip -> System.out.println("  " + zip));
  }

  private static String replaceZip(String zip) {
    String normalized = normalizeZip(zip);
    return ZIP_REPLACEMENTS.getOrDefault(normalized, normalized);
  }

  private static Map<String, List<Coord>> loadCoordinatesByZip(String coordinatesWithZips) throws IOException {
    Map<String, List<Coord>> coordsByZip = new HashMap<>();
    try (CSVReader coordinatesReader = new CSVReader(coordinatesWithZips, SEP)) {
      Map<String, String> row;
      while ((row = coordinatesReader.readLine()) != null) {
        String zip = normalizeZip(row.get("ZIP"));
        double x = Double.parseDouble(row.get("X"));
        double y = Double.parseDouble(row.get("Y"));
        coordsByZip.computeIfAbsent(zip, ignored -> new ArrayList<>()).add(new Coord(x, y));
      }
    }

    for (Map.Entry<String, List<Coord>> entry : coordsByZip.entrySet()) {
      entry.setValue(selectSpatiallyDistributedPoints(entry.getValue(), MAX_COORDS_PER_ZIP));
    }

    return coordsByZip;
  }

  private static List<Coord> selectSpatiallyDistributedPoints(List<Coord> allPoints, int maxPoints) {
    if (allPoints.size() <= maxPoints) {
      return allPoints;
    }

    double centerX = 0.0;
    double centerY = 0.0;
    for (Coord point : allPoints) {
      centerX += point.getX();
      centerY += point.getY();
    }
    centerX /= allPoints.size();
    centerY /= allPoints.size();

    List<Coord> selected = new ArrayList<>(maxPoints);
    Coord first = null;
    double maxCenterDistance = -1.0;
    for (Coord point : allPoints) {
      double d2 = squaredDistance(point.getX(), point.getY(), centerX, centerY);
      if (d2 > maxCenterDistance) {
        maxCenterDistance = d2;
        first = point;
      }
    }
    selected.add(first);

    while (selected.size() < maxPoints) {
      Coord bestCandidate = null;
      double bestMinDistance = -1.0;

      for (Coord candidate : allPoints) {
        double minDistanceToSelected = Double.POSITIVE_INFINITY;
        for (Coord chosen : selected) {
          double d2 = squaredDistance(candidate.getX(), candidate.getY(), chosen.getX(), chosen.getY());
          if (d2 < minDistanceToSelected) {
            minDistanceToSelected = d2;
          }
        }

        if (minDistanceToSelected > bestMinDistance) {
          bestMinDistance = minDistanceToSelected;
          bestCandidate = candidate;
        }
      }

      if (bestCandidate == null || bestMinDistance == 0.0) {
        break;
      }
      selected.add(bestCandidate);
    }

    return selected;
  }

  private static double squaredDistance(double x1, double y1, double x2, double y2) {
    double dx = x1 - x2;
    double dy = y1 - y2;
    return dx * dx + dy * dy;
  }

  private static List<Coord> resolveCoords(
      String zip,
      String municipality,
      Map<String, List<Coord>> coordsByZip,
      Map<String, Coord> specialCases
  ) {
    Coord specialCaseCoord = specialCases.get(municipality);
    if (specialCaseCoord != null) {
      return List.of(specialCaseCoord);
    }
    return coordsByZip.getOrDefault(zip, List.of());
  }

  private static Tuple<Double, Double> calculateAveragedPkm(
      SwissRailRaptor raptor,
      Scenario scenario,
      List<Coord> fromCoords,
      List<Coord> toCoords,
      Set<Id<TransitLine>> railLines
  ) {
    if (fromCoords.isEmpty() || toCoords.isEmpty()) {
      return new Tuple<>(Double.NaN, Double.NaN);
    }

    double railPkmSum = 0.;
    double nonRailPkmSum = 0.;
    int routeCount = 0;

    for (Coord fromCoord : fromCoords) {
      for (Coord toCoord : toCoords) {
        var route = raptor.calcRoute(DefaultRoutingRequest.withoutAttributes(
            new LinkWrapperFacilityWithSpecificCoord(
                NetworkUtils.getNearestLink(scenario.getNetwork(), fromCoord),
                fromCoord
            ),
            new LinkWrapperFacilityWithSpecificCoord(
                NetworkUtils.getNearestLink(scenario.getNetwork(), toCoord),
                toCoord
            ),
            DEPARTURE_TIME,
            null
        ));

        if (route == null) {
          continue;
        }

        Tuple<Double, Double> railAndOtherPkm = getRailAndOtherPkm(route, railLines);
        railPkmSum += railAndOtherPkm.getFirst();
        nonRailPkmSum += railAndOtherPkm.getSecond();
        routeCount++;
      }
    }

    if (routeCount == 0) {
      return new Tuple<>(Double.NaN, Double.NaN);
    }

    return new Tuple<>(railPkmSum / routeCount, nonRailPkmSum / routeCount);
  }

  private static String normalizeZip(String zipRaw) {
    String zip = zipRaw == null ? "" : zipRaw.trim();
    if (zip.endsWith(".0")) {
      zip = zip.substring(0, zip.length() - 2);
    }
    return zip;
  }

  private static String formatDouble(double value) {
    if (Double.isNaN(value)) {
      return "";
    }
    return String.format(Locale.ROOT, "%.6f", value);
  }

  private static Tuple<Double, Double> getRailAndOtherPkm(List<? extends PlanElement> route, Set<Id<TransitLine>> railLines) {
    double railPkm = 0.;
    double nonRailPkm = 0.;
    for (PlanElement planElement : route) {
      if (planElement instanceof Leg leg && leg.getRoute() instanceof TransitPassengerRoute transitPassengerRoute) {
        if (railLines.contains(transitPassengerRoute.getLineId())) {
          railPkm += transitPassengerRoute.getDistance();
        } else {
          nonRailPkm += transitPassengerRoute.getDistance();
        }
      }
    }
    return new Tuple<>(railPkm / 1000., nonRailPkm / 1000.);
  }

  private record RelationKey(String fromZip, String fromMun, String toZip, String toMun) {
    private RelationKey {
      fromZip = safe(fromZip);
      fromMun = safe(fromMun);
      toZip = safe(toZip);
      toMun = safe(toMun);
    }

    private static String safe(String value) {
      return value == null ? "" : value;
    }
  }

  private record RelationRow(String fromZip, String fromMun, String toZip, String toMun) {
  }
}
