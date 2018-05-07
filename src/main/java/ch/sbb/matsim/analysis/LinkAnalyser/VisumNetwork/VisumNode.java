package ch.sbb.matsim.analysis.LinkAnalyser.VisumNetwork;

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.network.Node;

import java.util.concurrent.atomic.AtomicInteger;


public class VisumNode {
    private static final AtomicInteger count = new AtomicInteger(100000000);
    private int id;
    private final Node node;

    public int getId() {
        return id;
    }

    public Coord getCoord(){
        return this.node.getCoord();
    }

    public VisumNode(Node node) {
        this.id = count.incrementAndGet();
        this.node = node;
    }

}
