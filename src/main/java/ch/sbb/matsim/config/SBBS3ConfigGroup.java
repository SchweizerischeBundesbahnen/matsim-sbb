/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2017.
 */

package ch.sbb.matsim.config;

import java.util.Map;

import org.matsim.core.config.ReflectiveConfigGroup;

/**
 */
public class SBBS3ConfigGroup extends ReflectiveConfigGroup {

    static public final String GROUP_NAME = "SBBS3";

    static private final String PARAM_FOLDER = "downloadFolder";
    static private final String PARAM_USE = "useS3Downloader";

    private String downloadFolder = ".s3_data/";
    private boolean useS3Downloader = false;

    public SBBS3ConfigGroup() {
        super(GROUP_NAME);
    }


    @StringGetter(PARAM_USE)
    public boolean getUseS3Downloader() {
        return this.useS3Downloader;
    }

    @StringSetter(PARAM_USE)
    public void setUseS3Downloader(boolean use) {
        this.useS3Downloader = use;
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
        comments.put(PARAM_FOLDER, "Download folder for s3 data");
        return comments;
    }
}
