package ch.sbb.matsim.analysis.LinkAnalyser.VisumNetwork;


import org.matsim.api.core.v01.network.Link;

import java.util.concurrent.atomic.AtomicInteger;

public class VisumLink {

    private static final AtomicInteger count = new AtomicInteger(1);


    public double getVolume() {
        return volume;
    }

    private double volume = 0;

    public VisumNode getFromNode() {
        return fromNode;
    }

    private final VisumNode fromNode;

    public VisumNode getToNode() {
        return toNode;
    }

    private final VisumNode toNode;

    public int getId() {
        return id;
    }

    private final int id;

    public Link getMATSimLink() {
        return link;
    }

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

    public void setMATSimLink(Link link){
        this.link = link;
    }

    public void setVolume(double volume) {
        this.volume = volume;
    }

    public VisumLink create_opposite_direction() {
        return new VisumLink(this.toNode, this.fromNode, this.id);
    }
}
