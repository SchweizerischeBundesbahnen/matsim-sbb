package ch.sbb.matsim.s3;

import ch.sbb.matsim.config.SBBS3ConfigGroup;
import java.io.File;
import java.util.Collection;
import java.util.Map;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigGroup;
import org.matsim.core.config.ConfigUtils;

// AWS SDK v2 Imports
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.S3Object;

public class S3Downloader {

    private static final Logger log = LogManager.getLogger(S3Downloader.class);

    private S3Client s3;
    private String downloadFolder;
    private final Config config;

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

        // Updated client builder for v2
        this.s3 = S3Client.builder()
                .region(Region.EU_CENTRAL_1)
                .build();

        this.parseConfig();
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

    private boolean isS3url(String path) {
        return (path != null && path.startsWith(this.downloadFolder));
    }

    private File getLocalPath(String key, String bucketName) {
        return new File(this.downloadFolder, bucketName + "/" + key);
    }

    private void download(String bucketName, String key, File target) {
        log.info("{} -> {}", key, target);

        // V1 automatically created parent directories; V2 does not. Ensure they exist.
        if (target.getParentFile() != null) {
            target.getParentFile().mkdirs();
        }

        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build();

        s3.getObject(getObjectRequest, target.toPath());
    }

    private void parseConfigGroup(ConfigGroup configGroup) {

        try {
            Map<String, String> params = configGroup.getParams();

            for (Map.Entry<String, String> entry : params.entrySet()) {
                String value = entry.getValue();

                if (isS3url(value) && !configGroup.getName().equals(SBBS3ConfigGroup.GROUP_NAME)) {

                    log.info(value);
                    String key = value.replace(this.downloadFolder, "");
                    String bucketName = key.split("/")[0];

                    String keyWithoutBucket = key.replace(bucketName + "/", "");

                    if (key.endsWith(".shp") || key.endsWith(".SHP")) {
                        String s3Folder = new File(keyWithoutBucket).getParent().replace("\\", "/") + "/";

                        // Updated to use ListObjectsV2Request and the Paginator
                        ListObjectsV2Request listReq = ListObjectsV2Request.builder()
                                .bucket(bucketName)
                                .prefix(s3Folder)
                                .build();

                        for (S3Object s3Object : s3.listObjectsV2Paginator(listReq).contents()) {
                            if (!s3Object.key().endsWith("/")) {
                                File target = getLocalPath(s3Object.key(), bucketName);
                                download(bucketName, s3Object.key(), target);
                            }
                        }
                    } else {
                        File target = getLocalPath(keyWithoutBucket, bucketName);
                        log.info("Downloading bucket: {} key: {} to {}", bucketName, key, target);
                        download(bucketName, keyWithoutBucket, target);
                    }

                }
            }

        } catch (RuntimeException a) {
            log.warn("An error occurred parsing the S3 config group", a);
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
}