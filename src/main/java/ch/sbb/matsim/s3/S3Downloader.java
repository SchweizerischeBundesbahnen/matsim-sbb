package ch.sbb.matsim.s3;

import java.io.File;
import java.util.Map;

import ch.sbb.matsim.config.SBBS3ConfigGroup;
import org.apache.log4j.Logger;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigGroup;
import org.matsim.core.config.ConfigUtils;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.GetObjectRequest;

public class S3Downloader {
    private static Logger log = Logger.getLogger(S3Downloader.class);

    private final String bucketName;
    private final AmazonS3 s3;
    private final String downloadFolder;
    private final String s3Prefix = "s3://";
    private Config config;

    public S3Downloader(Config config) {
        this.config = config;

        SBBS3ConfigGroup s3Config = ConfigUtils.addOrGetModule(config, SBBS3ConfigGroup.GROUP_NAME, SBBS3ConfigGroup.class);

        this.bucketName = s3Config.getBucket();
        this.downloadFolder = s3Config.getDownloadFolder();

        s3 = AmazonS3ClientBuilder.defaultClient();

        this.parseConfig();
    }

    private boolean isS3url(String path) {
        return (path.startsWith(this.s3Prefix));
    }

    private File getLocalPath(String key) {
        return new File(this.downloadFolder, key);

    }

    private void download(String key, File target) {
        s3.getObject(new GetObjectRequest(bucketName, key), target);
    }

    private void parseConfigGroup(ConfigGroup configGroup) {

        try {
            Map<String, String> params = configGroup.getParams();
            for (Map.Entry<String, String> entry : params.entrySet()) {
                if (isS3url(entry.getValue())) {
                    if (isS3url(entry.getValue())) {
                        String key = entry.getValue().replace(this.s3Prefix, "");
                        File target = getLocalPath(key);

                        log.info("Downloading "+entry.getValue()+" to "+ target +" and updating config");
                        download(key, target);
                        configGroup.addParam(entry.getKey(), target.toString());
                    }
                }
            }

        } catch (RuntimeException a) {
            log.warn(a);
        }
    }

    private void parseConfig() {

        for (ConfigGroup configGroup : this.config.getModules().values()) {
            parseConfigGroup(configGroup);
        }

    }

    public static void main(String[] args) {
        Config config = ConfigUtils.createConfig(new SBBS3ConfigGroup());
        SBBS3ConfigGroup s3Config = ConfigUtils.addOrGetModule(config, SBBS3ConfigGroup.GROUP_NAME, SBBS3ConfigGroup.class);

        s3Config.setBucket("blabla");

        config.network().setInputFile("s3://vehicles.xml.gz");

        S3Downloader s3Downloader = new S3Downloader(config);

        log.info(config.network().getInputFile());

    }
}
