package ch.sbb.matsim.zones;

import org.apache.commons.io.FilenameUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.geotools.api.data.DataStore;
import org.geotools.api.data.DataStoreFinder;
import org.geotools.api.data.FileDataStore;
import org.geotools.api.data.FileDataStoreFinder;
import org.geotools.api.data.SimpleFeatureSource;
import org.geotools.api.feature.Feature;
import org.geotools.api.feature.simple.SimpleFeature;
import org.geotools.api.feature.simple.SimpleFeatureType;
import org.geotools.api.referencing.crs.CoordinateReferenceSystem;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.matsim.core.api.internal.MatsimSomeReader;
import org.matsim.core.gbl.Gbl;
import org.matsim.core.utils.misc.Counter;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.*;

/**
 * @author glaemmel
 * @author dgrether
 * @author mrieser // switch to GeoTools 2.7.3
 */
public class ShapeFileReader implements MatsimSomeReader {
    private static final Logger log = LogManager.getLogger(org.matsim.core.utils.gis.ShapeFileReader.class);

    private SimpleFeatureSource featureSource = null;

    private ReferencedEnvelope bounds = null;

    private DataStore dataStore = null;

    private SimpleFeatureCollection featureCollection = null;

    private SimpleFeatureType schema = null;

    private Collection<SimpleFeature> featureSet = null;

    private CoordinateReferenceSystem crs;

    public static Collection<SimpleFeature> getAllFeatures(final String filename) {
        try {
            File dataFile = new File(filename);
            log.info( "will try to read from " + dataFile.getAbsolutePath() ) ;
            Gbl.assertIf( dataFile.exists() );
            DataStore dataStore = ShapeFileReader.getDataStore(filename);
            SimpleFeatureSource featureSource = dataStore.getFeatureSource(FilenameUtils.getBaseName(filename));
            List<SimpleFeature> featureSet = getSimpleFeatures(featureSource);
            dataStore.dispose();
            return featureSet;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static DataStore getDataStore(Object path) {
        try {
            if (path instanceof String) {
                String filename = String.valueOf(path);
                if (filename.toLowerCase(Locale.ROOT).endsWith(".shp")) {
                    return FileDataStoreFinder.getDataStore(new File(filename));
                } else if (filename.toLowerCase(Locale.ROOT).endsWith(".geopkg") |
                           filename.toLowerCase(Locale.ROOT).endsWith(".geopackage") |
                           filename.toLowerCase(Locale.ROOT).endsWith(".gpkg")) {
                    Map<String, Object> map = new HashMap<>();
                    map.put("dbtype", "geopkg");
                    map.put("database", filename);
                    map.put("read-only", true);
                    return DataStoreFinder.getDataStore(map);
                }
            } else if (path instanceof URL) {
                return FileDataStoreFinder.getDataStore((URL) path);
            }
        } catch (IOException e) {
            log.error("Could not load shapes from " + path);
            throw new RuntimeException(e);
        }
        throw new RuntimeException("Couldn't find a data store for " + path);
    }

    public static Collection<SimpleFeature> getAllFeatures(final URL url) {
        try {
            log.info( "will try to read from " + url.getPath() ) ;
            DataStore dataStore = ShapeFileReader.getDataStore(url);
            SimpleFeatureSource featureSource = dataStore.getFeatureSource(FilenameUtils.getBaseName(url.getPath()));
            List<SimpleFeature> featureSet = getSimpleFeatures(featureSource);
            dataStore.dispose();
            return featureSet;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Read all simple features from a data store. This method makes sure the store is closed afterwards.
     * @return list of contained features.
     */
    public static List<SimpleFeature> getSimpleFeatures(SimpleFeatureSource featureSource) throws IOException {
        SimpleFeatureIterator it = featureSource.getFeatures().features();
        List<SimpleFeature> featureSet = new ArrayList<>();
        while (it.hasNext()) {
            SimpleFeature ft = it.next();
            featureSet.add(ft);
        }
        it.close();
        return featureSet;
    }

    /**
     * <em>VERY IMPORTANT NOTE</em><br>
     * <p></p>
     * There are many ways to use that class in a wrong way. The safe way is the following:
     * <p></p>
     * <pre> ShapeFileReader shapeFileReader = new ShapeFileReader();
     * shapeFileReader.readFileAndInitialize(zonesShapeFile); </pre>
     * <p></p>
     * Then, get the features by
     * <p></p>
     * <pre> Set<{@link Feature}> features = shapeFileReader.getFeatureSet(); </pre>
     * <p></p>
     * If you need metadata you can use
     * <p></p>
     * <pre> FeatureSource fs = shapeFileReader.getFeatureSource(); </pre>
     * <p></p>
     * to get access to the feature source.<br>
     * <em>BUT NEVER CALL <code>fs.getFeatures();</code> !!! It can happen that you will read from disk again!!! </em>
     * <p></p>
     * <p>
     * Actually, the whole class must be fixed. But since it is anyway necessary to move to a more recent version of the geotools only this javadoc is added instead.
     * </p>
     * <p></p>
     * <p>
     * The following old doc is kept here:
     * </p>
     * <p></p>
     * Provides access to a shape file and returns a <code>FeatureSource</code> containing all features.
     * Take care access means on disk access, i.e. the FeatureSource is only a pointer to the information
     * stored in the file. This can be horribly slow if invoked many times and throw exceptions if two many read
     * operations to the same file are performed. In those cases it is recommended to use the method readDataFileToMemory
     * of this class.
     *
     * @param filename File name of a shape file (ending in <code>*.shp</code>)
     * @return FeatureSource containing all features.
     * @throws RuntimeException if the file cannot be found or another error happens during reading
     */
    public static SimpleFeatureSource readDataFile(final String filename) throws RuntimeException {
        try {
            log.warn("Unsafe method! store.dispose() is not called from within this method");
            File dataFile = new File(filename);
            FileDataStore store = FileDataStoreFinder.getDataStore(dataFile);
            return store.getFeatureSource();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Reads all Features in the file into the returned Set and initializes the instance of this class.
     */
    public Collection<SimpleFeature> readFileAndInitialize(final String filename) {
        try {
            this.featureSource = org.matsim.core.utils.gis.ShapeFileReader.readDataFile(filename);
            this.init();
            SimpleFeature ft = null;
            SimpleFeatureIterator it = this.featureSource.getFeatures().features();
            this.featureSet = new ArrayList<SimpleFeature>();
            log.info("features to read #" + this.featureSource.getFeatures().size());
            Counter cnt = new Counter("features read #");
            while (it.hasNext()) {
                ft = it.next();
                this.featureSet.add(ft);
                cnt.incCounter();
            }
            cnt.printCounter();
            it.close();
            return this.featureSet;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void init() {
        try {
            this.bounds = this.featureSource.getBounds();
            this.dataStore = (DataStore) this.featureSource.getDataStore();
            this.featureCollection = this.featureSource.getFeatures();
            this.schema = this.featureSource.getSchema();
            this.crs = this.featureSource.getSchema().getCoordinateReferenceSystem();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public SimpleFeatureSource getFeatureSource() {
        return featureSource;
    }

    public ReferencedEnvelope getBounds() {
        return bounds;
    }

    public DataStore getDataStore() {
        return dataStore;
    }

    public SimpleFeatureCollection getFeatureCollection() {
        return featureCollection;
    }

    public SimpleFeatureType getSchema() {
        return schema;
    }

    public Collection<SimpleFeature> getFeatureSet() {
        return featureSet;
    }

    public CoordinateReferenceSystem getCoordinateSystem(){
        return this.crs;
    }


}
