package ch.sbb.matsim.s3;

import java.io.File;
import java.util.Map;

import org.apache.log4j.Logger;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigGroup;
import org.matsim.core.config.ConfigUtils;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.GetObjectRequest;

public class S3Downloader {
    private static Logger log = Logger.getLogger(S3Downloader.class);

    Config config;
    String bucketName;
    final AmazonS3 s3;
    String downloadFolder;

    public S3Downloader(Config config, String bucketName, String downloadFolder) {
        this.config = config;
        this.bucketName = bucketName;
        this.downloadFolder = downloadFolder;
        s3 = AmazonS3ClientBuilder.defaultClient();
    }

    private boolean isS3url(String path) {
        return (path.startsWith("s3://"));
    }

    private File getLocalPath(String key){
        return new File(this.downloadFolder, key);

    }

    private void download(String key, File target) {
        s3.getObject(new GetObjectRequest(bucketName, key), target);
    }

    private ConfigGroup parseConfigGroup(ConfigGroup configGroup) {

        try {
            Map<String, String> params = configGroup.getParams();
            for (Map.Entry<String, String> entry : params.entrySet()) {
                if (isS3url(entry.getValue())) {
                    if (isS3url(entry.getValue())) {
                        System.out.println(entry.getKey() + "/" + entry.getValue());
                        String key = entry.getValue().replace("s3://", "");

                        File target = getLocalPath(key);
                        download(key, target);
                        configGroup.addParam(entry.getKey(), entry.getValue());
                    }
                }
            }

        } catch (RuntimeException a) {
            log.warn(a);
        }
        return configGroup;
    }

    private void parseConfig() {
        this.config.set

        for (ConfigGroup configGroup : this.config.getModules().values()) {
            ConfigGroup configGroup1 = parseConfigGroup(configGroup);

        }
    }

    public static void main(String[] args) {
        Config config = ConfigUtils.createConfig();

        config.network().setInputFile("s3://vehicles.xml.gz");

        S3Downloader s3Downloader = new S3Downloader(config, "sbb-memop-nonprod", "s3_data");

        s3Downloader.parseConfig();

        log.info(config.network().getInputFile());

    }
}
