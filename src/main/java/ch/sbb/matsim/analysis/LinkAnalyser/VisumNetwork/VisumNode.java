package ch.sbb.matsim.analysis.LinkAnalyser.VisumNetwork;

import java.util.concurrent.atomic.AtomicInteger;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.network.Node;

public class VisumNode {

	private static final AtomicInteger count = new AtomicInteger(100000000);
	private final Node node;
	private int id;

	public VisumNode(Node node) {
		this.id = count.incrementAndGet();
		this.node = node;
	}

	public int getId() {
		return id;
	}

	public Coord getCoord() {
		return this.node.getCoord();
	}

}
