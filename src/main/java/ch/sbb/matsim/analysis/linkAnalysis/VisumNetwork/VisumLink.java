package ch.sbb.matsim.analysis.linkAnalysis.VisumNetwork;

import java.util.concurrent.atomic.AtomicInteger;
import org.matsim.api.core.v01.network.Link;

public class VisumLink {

	private static final AtomicInteger count = new AtomicInteger(1000000000);
	private final VisumNode fromNode;
	private final VisumNode toNode;
	private final int id;
	private double volume = 0;
	private Link link = null;

	public VisumLink(VisumNode fromNode, VisumNode toNode) {
		this.fromNode = fromNode;
		this.toNode = toNode;
		this.id = count.incrementAndGet();
	}

	public VisumLink(VisumNode fromNode, VisumNode toNode, Integer id) {
		this.fromNode = fromNode;
		this.toNode = toNode;
		this.id = id;
	}

	public double getVolume() {
		return volume;
	}

	public void setVolume(double volume) {
		this.volume = volume;
	}

	public VisumNode getFromNode() {
		return fromNode;
	}

	public VisumNode getToNode() {
		return toNode;
	}

	public int getId() {
		return id;
	}

	public Link getMATSimLink() {
		return link;
	}

	public void setMATSimLink(Link link) {
		this.link = link;
	}

	public VisumLink create_opposite_direction() {
		return new VisumLink(this.toNode, this.fromNode, this.id);
	}
}
