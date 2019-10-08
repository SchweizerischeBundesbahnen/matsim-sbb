package ch.sbb.matsim.s3;

import java.io.File;
import java.util.Collection;
import java.util.Map;

import ch.sbb.matsim.config.SBBS3ConfigGroup;
import com.amazonaws.services.s3.model.ListObjectsRequest;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import org.apache.log4j.Logger;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigGroup;
import org.matsim.core.config.ConfigUtils;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.GetObjectRequest;

public class S3Downloader {

    private static Logger log = Logger.getLogger(S3Downloader.class);

    private AmazonS3 s3;
    private String downloadFolder;
    private Config config;

    public S3Downloader(Config config) {


        this.config = config;
        SBBS3ConfigGroup s3Config = ConfigUtils.addOrGetModule(config, SBBS3ConfigGroup.GROUP_NAME, SBBS3ConfigGroup.class);

        if (!s3Config.getUseS3Downloader()) {
            log.info("Skipping S3 Downloader");
            return;
        }

        System.setProperty("org.apache.commons.logging.Log",
                "org.apache.commons.logging.impl.NoOpLog");

        this.downloadFolder = s3Config.getDownloadFolder();

        this.s3 = AmazonS3ClientBuilder.standard().withRegion("eu-central-1").build();

        this.parseConfig();

    }

    private boolean isS3url(String path) {
        return (path.startsWith(this.downloadFolder));
    }

    private File getLocalPath(String key, String bucketName) {
        return new File(this.downloadFolder, bucketName +"/"+ key);

    }

    private void download(String bucketName, String key, File target) {
        log.info(key + " -> " + target);
        s3.getObject(new GetObjectRequest(bucketName, key), target);

    }

    private void parseConfigGroup(ConfigGroup configGroup) {

        try {
            Map<String, String> params = configGroup.getParams();


            for (Map.Entry<String, String> entry : params.entrySet()) {

                if (isS3url(entry.getValue()) && !configGroup.getName().equals(SBBS3ConfigGroup.GROUP_NAME)) {

                    log.info(entry.getValue());
                    String key = entry.getValue().replace(this.downloadFolder, "");
                    String bucketName = key.split("/")[0];

                    String keyWithoutBucket = key.replace(bucketName + "/", "");
                    if (key.endsWith(".shp") || key.endsWith(".SHP")) {
                        String s3Folder = new File(keyWithoutBucket).getParent().replace("\\", "/") + "/";
                        for (S3ObjectSummary sum : s3.listObjects(new ListObjectsRequest().withBucketName(bucketName).withPrefix(s3Folder)).getObjectSummaries()) {
                            if (!sum.getKey().endsWith("/")) {
                                File target = getLocalPath(sum.getKey(), bucketName);
                                download(bucketName, sum.getKey(), target);
                            }
                        }
                    } else {
                        File target = getLocalPath(keyWithoutBucket, bucketName);
                        log.info("Downloading " + bucketName + " " + key + " to " + target);
                        download(bucketName, keyWithoutBucket, target);
                    }

                }
            }

        } catch (RuntimeException a) {
            log.warn(a);
        }
    }

    private void parseConfig() {


        for (ConfigGroup configGroup : this.config.getModules().values()) {
            for (Collection<? extends ConfigGroup> paramSet : configGroup.getParameterSets().values()) {
                for (ConfigGroup group : paramSet) {
                    parseConfigGroup(group);
                }
            }

            parseConfigGroup(configGroup);
        }

    }

    public static void main(String[] args) {
        //Config config = ConfigUtils.loadConfig("\\\\k13536\\MOBi\\sim\\runs\\CH\\2016\\9.9.9_test_aws\\aws_config_scoring_parsed.xml");
        Config config = ConfigUtils.createConfig(new SBBS3ConfigGroup());
        SBBS3ConfigGroup s3Config = ConfigUtils.addOrGetModule(config, SBBS3ConfigGroup.GROUP_NAME, SBBS3ConfigGroup.class);
        s3Config.setUseS3Downloader(true);
        config.network().setInputFile(".s3_data/mobi-model-bucket/test.txt");
        //config.network().setInputFile("s3://mobi-model-bucket/runs/CH/test_year/test_dm/run.sh");

        S3Downloader s3Downloader = new S3Downloader(config);

        log.info(config.network().getInputFile());

    }
}
