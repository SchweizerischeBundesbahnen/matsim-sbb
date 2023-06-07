/* *********************************************************************** *
 * project: org.matsim.*												   *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2008 by the members listed in the COPYING,        *
 *                   LICENSE and WARRANTY file.                            *
 * email           : info at matsim dot org                                *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *   See also COPYING, LICENSE and WARRANTY file                           *
 *                                                                         *
 * *********************************************************************** */
package ch.sbb.matsim.mavi.streets;

import org.geotools.coverage.grid.GridCoordinates2D;
import org.geotools.coverage.grid.GridCoverage2D;
import org.geotools.coverage.grid.GridGeometry2D;
import org.geotools.coverage.grid.InvalidGridGeometryException;
import org.geotools.data.DataSourceException;
import org.geotools.gce.geotiff.GeoTiffReader;
import org.geotools.geometry.DirectPosition2D;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.core.network.io.NetworkWriter;
import org.matsim.core.utils.geometry.CoordinateTransformation;
import org.matsim.core.utils.geometry.transformations.TransformationFactory;
import org.opengis.referencing.operation.TransformException;

import java.awt.image.Raster;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author smetzler, dziemke, jfbischoff
 */
public class ElevationDataParser {

    //using epsg codes creates a mess on our servers, so for everyone's sake we use the full WKT definition here.
    public static final String EPSG_3035 = "PROJCS[\"ETRS_1989_LAEA\",GEOGCS[\"GCS_ETRS_1989\",DATUM[\"D_ETRS_1989\",SPHEROID[\"GRS_1980\",6378137.0,298.257222101]],PRIMEM[\"Greenwich\",0.0],UNIT[\"Degree\",0.0174532925199433]],PROJECTION[\"Lambert_Azimuthal_Equal_Area\"],PARAMETER[\"False_Easting\",4321000.0],PARAMETER[\"False_Northing\",3210000.0],PARAMETER[\"Central_Meridian\",10.0],PARAMETER[\"Latitude_Of_Origin\",52.0],UNIT[\"Meter\",1.0]]";
    private static GridCoverage2D grid;
    private static Raster gridData;
    private CoordinateTransformation ct;

    public ElevationDataParser(String tiffFile, String scenarioCRS) {
        this.ct = TransformationFactory.getCoordinateTransformation(scenarioCRS, EPSG_3035);

        GeoTiffReader reader = null;
        try {
            reader = new GeoTiffReader(tiffFile);
        } catch (DataSourceException e) {
            e.printStackTrace();
        }

        try {
            grid = reader.read(null);
        } catch (IOException e) {
            e.printStackTrace();
        }
        gridData = grid.getRenderedImage().getData();
        System.out.println(gridData.getBounds());
    }

    public static void main(String[] args) {
        // Data sources:
        // SRTM1:  http://earthexplorer.usgs.gov/ (login in required)
        // SRTM3:  http://srtm.csi.cgiar.org/SELECTION/inputCoord.asp
        // EU-DEM: http://data.eox.at/eudem
        String inputNetwork = args[0];
        String elevationModelFile = args[1];
        String outputNetworkFile = args[2];


        addElevationDataToNetwork(inputNetwork, elevationModelFile, outputNetworkFile);
    }

    public static void addElevationDataToNetwork(String inputNetwork, String elevationModelFile, String outputNetworkFile) {
        String scenarioCRS = "EPSG:2056"; // WGS84 as the coorinates to test below are stated like this
        Network network = NetworkUtils.createNetwork();
        new MatsimNetworkReader(network).readFile(inputNetwork);
        addElevationDataToNetwork(elevationModelFile, scenarioCRS, network);
        NetworkWriter networkWriter = new NetworkWriter(network);
        networkWriter.write(outputNetworkFile);
    }

    public static void addElevationDataToNetwork(String elevationModelFile, String scenarioCRS, Network network) {
        AtomicInteger zs = new AtomicInteger();

        ElevationDataParser elevationDataParser = new ElevationDataParser(elevationModelFile, scenarioCRS);
        network.getNodes().values().parallelStream().filter(node -> !node.getCoord().hasZ()).forEach(node -> {
            Double z = elevationDataParser.getElevation(node.getCoord());
            if (z != null) {
                if (z < -10) {
                    z = 0.0;
                }
                node.setCoord(new Coord(node.getCoord().getX(), node.getCoord().getY(), z));
                //for VIA viz only
                node.getAttributes().putAttribute("z", z);
                zs.getAndIncrement();
            }
        });


        network.getNodes().values().stream().filter(n -> !n.getCoord().hasZ()).forEach(n -> n.setCoord(new Coord(n.getCoord().getX(), n.getCoord().getY(), 400)));

        System.out.println(zs + " z coordinates set");
    }

    public Double getElevation(Coord coord) {
        GridGeometry2D gg = grid.getGridGeometry();

        Coord transformedCoord = ct.transform(coord);
        GridCoordinates2D posGrid = null;
        try {
            posGrid = gg.worldToGrid(new DirectPosition2D(transformedCoord.getX(), transformedCoord.getY()));
        } catch (InvalidGridGeometryException e) {
            e.printStackTrace();
        } catch (TransformException e) {
            e.printStackTrace();
        }

        double[] pixel = new double[1];
        try {
            double[] data = gridData.getPixel(posGrid.x, posGrid.y, pixel);
            return data[0];
        } catch (ArrayIndexOutOfBoundsException e) {
            return null;

        }
    }
}