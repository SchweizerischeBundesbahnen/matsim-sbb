package ch.sbb.matsim.umlego;

import com.opencsv.CSVWriter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.core.utils.io.IOUtils;
import org.matsim.core.utils.misc.Time;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

class UmlegoWriter implements Runnable {

	private static final Logger LOG = LogManager.getLogger(UmlegoWriter.class);

	private final BlockingQueue<Future<UmlegoWorker.WorkResult>> queue;
	private final String csvFilename;
	private final List<String> zoneIds;
	private final CompletableFuture<Umlego.UnroutableDemand> futureUnroutableDemand = new CompletableFuture<>();
	private final Umlego.WriterParameters params;

	public UmlegoWriter(BlockingQueue<Future<UmlegoWorker.WorkResult>> queue, String csvFilename, List<String> zoneIds, Umlego.WriterParameters params) {
		this.queue = queue;
		this.csvFilename = csvFilename;
		this.zoneIds = zoneIds;
		this.params = params;
	}

	@Override
	public void run() {
		Umlego.UnroutableDemand unroutableDemand = this.writeRoutes();
		this.futureUnroutableDemand.complete(unroutableDemand);
	}

	private Umlego.UnroutableDemand writeRoutes() {
		LOG.info("writing output to {}", csvFilename);
		Umlego.UnroutableDemand unroutableDemand = new Umlego.UnroutableDemand();
		int totalItems = this.zoneIds.size();
		int counter = 0;
		try (CSVWriter writer = new CSVWriter(IOUtils.getBufferedWriter(csvFilename), ',', '"', '\\', "\n")) {
			writer.writeNext(new String[]{"ORIGZONENO", "DESTZONENO", "ORIGNAME", "DESTNAME", "ACCESS_TIME", "EGRESS_TIME", "DEPTIME", "ARRTIME", "TRAVTIME", "NUMTRANSFERS", "DISTANZ", "DEMAND", "DETAILS"});

			while (true) {
				Future<UmlegoWorker.WorkResult> futureResult = this.queue.take();
				UmlegoWorker.WorkResult result = futureResult.get();
				if (result.originZone() == null) {
					// end marker, the work is done
					break;
				}

				counter++;
				LOG.info(" - writing routes starting in zone {} ({}/{})", result.originZone(), counter, totalItems);
				unroutableDemand.demand += result.unroutableDemand().demand;

				String origZone = result.originZone();
				Map<String, List<Umlego.FoundRoute>> routesPerDestination = result.routesPerDestinationZone();
				if (routesPerDestination == null) {
					// looks like this zone cannot reach any destination
					continue;
				}
				for (String destZone : zoneIds) {
					if (origZone.equals(destZone)) {
						// we're not interested in intrazonal trips
						continue;
					}
					List<Umlego.FoundRoute> routesToDestination = routesPerDestination.get(destZone);
					if (routesToDestination == null) {
						// looks like there are no routes to this destination from the given origin zone
						continue;
					}

					for (Umlego.FoundRoute route : routesToDestination) {
						if (this.params.writeRoutesWithoutDemand() || route.demand > 0) {
							writer.writeNext(new String[]{
									origZone,
									destZone,
									route.originStop.getName(),
									route.destinationStop.getName(),
									Time.writeTime(route.originConnectedStop.walkTime()),
									Time.writeTime(route.destinationConnectedStop.walkTime()),
									Time.writeTime(route.depTime),
									Time.writeTime(route.arrTime),
									Time.writeTime(route.travelTime),
									Integer.toString(route.transfers),
									String.format("%.2f", route.distance / 1000.0),
									String.format("%.5f", route.demand),
									this.params.writeDetails() ? route.getRouteAsString() : ""
							});
						}
					}
				}
			}
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		} catch (ExecutionException | InterruptedException e) {
			throw new RuntimeException(e);
		}
		return unroutableDemand;
	}

	public CompletableFuture<Umlego.UnroutableDemand> getUnroutableDemand() {
		return this.futureUnroutableDemand;
	}
}
