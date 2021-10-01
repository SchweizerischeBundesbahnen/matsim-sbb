package ch.sbb.matsim.analysis.linkAnalysis.ScreenLines;

import java.util.ArrayList;
import org.apache.log4j.Logger;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;

public class ScreenLine {

	private final static Logger log = Logger.getLogger(ScreenLine.class);
	private Geometry geometry;
	private ArrayList<Link> links;
	public ScreenLine(Geometry geometry) {
		this.geometry = geometry;
		this.links = new ArrayList<>();
	}

	public ArrayList<Link> getLinks() {
		return links;
	}

	public void findLinks(Network network) {
		GeometryFactory geometryFactory = new GeometryFactory();
		for (Link link : network.getLinks().values()) {
			Coordinate a = new Coordinate(link.getFromNode().getCoord().getX(), link.getFromNode().getCoord().getY());
			Coordinate b = new Coordinate(link.getToNode().getCoord().getX(), link.getToNode().getCoord().getY());
			Coordinate[] cs = new Coordinate[2];
			cs[0] = a;
			cs[1] = b;

			Geometry linkGeometry = geometryFactory.createLineString(cs);

			if (linkGeometry.intersects(this.geometry)) {
				this.links.add(link);
			}
		}

		log.info(this.links.size() + "  Links found");
	}
}
