package ch.sbb.matsim.umlego;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

class UmlegoWriter implements Runnable {

	private static final Logger LOG = LogManager.getLogger(UmlegoWriter.class);

	private final BlockingQueue<Future<UmlegoWorker.WorkResult>> queue;
	private final String filename;
	private final List<String> originZoneIds;
	private final List<String> destinationZoneIds;
	private final CompletableFuture<Umlego.UnroutableDemand> futureUnroutableDemand = new CompletableFuture<>();
	private final Umlego.WriterParameters params;

	public UmlegoWriter(BlockingQueue<Future<UmlegoWorker.WorkResult>> queue, String filename, List<String> originZoneIds, List<String> destinationZoneIds, Umlego.WriterParameters params) {
		this.queue = queue;
		this.filename = filename;
		this.originZoneIds = originZoneIds;
		this.destinationZoneIds = destinationZoneIds;
		this.params = params;
	}

	@Override
	public void run() {
		Umlego.UnroutableDemand unroutableDemand = this.writeRoutes();
		this.futureUnroutableDemand.complete(unroutableDemand);
	}

	private Umlego.UnroutableDemand writeRoutes() {
		LOG.info("writing output to {}", this.filename);
		Umlego.UnroutableDemand unroutableDemand = new Umlego.UnroutableDemand();
		int totalItems = this.originZoneIds.size();
		int counter = 0;
		try (PutSurveyWriter writer = new PutSurveyWriter(this.filename)) {
//		try (UmlegoCsvWriter writer = new UmlegoCsvWriter(this.filename, true)) {
			while (true) {
				Future<UmlegoWorker.WorkResult> futureResult = this.queue.take();
				UmlegoWorker.WorkResult result = futureResult.get();
				if (result.originZone() == null) {
					// end marker, the work is done
					break;
				}

				counter++;
				LOG.info(" - writing routes starting in zone {} ({}/{})", result.originZone(), counter, totalItems);
				unroutableDemand.parts.addAll(result.unroutableDemand().parts);
				String origZone = result.originZone();
				Map<String, List<Umlego.FoundRoute>> routesPerDestination = result.routesPerDestinationZone();
				if (routesPerDestination == null) {
					// looks like this zone cannot reach any destination
					continue;
				}
				for (String destZone : destinationZoneIds) {
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
						if (route.demand.getDouble(destZone) >= this.params.minimalDemandForWriting()) {
							writer.writeRoute(origZone, destZone, route);
						}
					}
				}
			}
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		return unroutableDemand;
	}

	public CompletableFuture<Umlego.UnroutableDemand> getUnroutableDemand() {
		return this.futureUnroutableDemand;
	}
}
