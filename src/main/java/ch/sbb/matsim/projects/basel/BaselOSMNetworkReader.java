package ch.sbb.matsim.projects.basel;

import ch.sbb.matsim.config.variables.SBBModes;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.NetworkWriter;
import org.matsim.contrib.osm.networkReader.LinkProperties;
import org.matsim.contrib.osm.networkReader.OsmBicycleReader;
import org.matsim.core.network.algorithms.NetworkCleaner;
import org.matsim.core.utils.geometry.CoordinateTransformation;
import org.matsim.core.utils.geometry.transformations.TransformationFactory;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

/**
 * 1. It is possible to apply a link filter. The below example includes all links which are of hierarchy level "motorway".
 * For simplicity filtering for a coordinate is skipped but this is the place to test whether a coordinate is within a
 * certain area. This method is called for the from- and to nodes of a link.
 * 2. The reader generally tries to simplify the network as much as possible. If it is necessary to preserve certain nodes
 * for e.g. implementing counts it is possible to omit the simplification for certain node ids. The below example
 * prevents the reader to remove the node with id: 2.
 * 3. It is possible to override the default properties wich are assigned to a link of a certain hierarchy level. E.g. one could change the freespeed of highways by adding a new LinkProperties object for the 'highway' tag. The example below adds LinkProperties for residential links, which are otherwise ignored.
 * 4. After creating a link the reader will call the 'afterLinkCreated' hook with the newly created link, the original osm
 * tags, and a flag whether it is the forward or reverse direction of an osm-way. The below example sets the allowed
 * transport mode on all links to 'car' and 'bike'.
 */
public class BaselOSMNetworkReader {

    private static final String inputFile = "\\\\wsbbrz0283\\mobi\\40_Projekte\\20240911_Fussgaenger_Oberwinterthur\\plans\\winterthur.pbf";
    private static final String outputFile = "\\\\wsbbrz0283\\mobi\\40_Projekte\\20240911_Fussgaenger_Oberwinterthur\\plans\\winterthur.xml.gz";
    private static final CoordinateTransformation coordinateTransformation = TransformationFactory.getCoordinateTransformation(TransformationFactory.WGS84, "EPSG:2056");

    public static void main(String[] args) {

        Network network = new OsmBicycleReader.Builder()
                .setCoordinateTransformation(coordinateTransformation)
                .addOverridingLinkProperties("corridor", new LinkProperties(12, 1, 1 / 3.6, 10000, false))
                .addOverridingLinkProperties("platform", new LinkProperties(13, 1, 1 / 3.6, 10000, false))
                .setAfterLinkCreated((link, osmTags, isReverse) -> link.setAllowedModes(new HashSet<>(List.of(SBBModes.WALK_MAIN_MAINMODE))))
                .build()
                .read(inputFile);
        new NetworkCleaner().run(network);

        new NetworkWriter(network).write(outputFile);
    }
}