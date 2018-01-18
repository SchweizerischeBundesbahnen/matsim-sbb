/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2017.
 */

package ch.sbb.matsim.config;

import java.util.Map;

import org.matsim.core.config.ReflectiveConfigGroup;

/**
 * @author mrieser / SBB
 */
public class SBBS3ConfigGroup extends ReflectiveConfigGroup {

    static public final String GROUP_NAME = "SBBS3";

    static private final String PARAM_BUCKET = "bucketName";
    static private final String PARAM_FOLDER = "downloadFolder";

    private String bucketName = "";
    private String downloadFolder = "s3_data";

    public SBBS3ConfigGroup() {
        super(GROUP_NAME);
    }

    @StringGetter(PARAM_BUCKET)
    public String getBucket() {
        return this.bucketName;
    }

    @StringSetter(PARAM_BUCKET)
    public void setBucket(String bucket) {
        this.bucketName = bucket;
    }


    @StringGetter(PARAM_FOLDER)
    public String getDownloadFolder() {
        return this.downloadFolder;
    }

    @StringSetter(PARAM_FOLDER)
    public void setDownloadFolder(String folder) {
        this.downloadFolder = folder;
    }

    @Override
    public Map<String, String> getComments() {
        Map<String, String> comments = super.getComments();
        comments.put(PARAM_BUCKET, "S3 Bucket name");
        comments.put(PARAM_FOLDER, "Download folder for s3 data");
        return comments;
    }
}
