package ch.sbb.matsim.analysis.LinkAnalyser.ScreenLines;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;

import java.util.ArrayList;

public class ScreenLine {
    private Geometry geometry;

    public ArrayList<Link> getLinks() {
        return links;
    }

    private ArrayList<Link> links;
    private final static Logger log = Logger.getLogger(ScreenLine.class);

    public ScreenLine(Geometry geometry) {
        this.geometry = geometry;
        this.links = new ArrayList<>();
    }

    public void findLinks(Network network){
        GeometryFactory geometryFactory = new GeometryFactory();
        for(Link link: network.getLinks().values()){
            Coordinate a = new Coordinate(link.getFromNode().getCoord().getX(), link.getFromNode().getCoord().getY());
            Coordinate b = new Coordinate(link.getToNode().getCoord().getX(), link.getToNode().getCoord().getY());
            Coordinate[] cs = new Coordinate[2];
            cs[0] = a;
            cs[1] = b;

            Geometry linkGeometry = geometryFactory.createLineString(cs);

            if(linkGeometry.intersects(this.geometry)){
                this.links.add(link);
            }
        }

        log.info(this.links.size()+"  Links found");
    }
}
