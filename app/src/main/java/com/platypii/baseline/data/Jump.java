package com.platypii.baseline.data;

import android.util.Log;

import com.google.firebase.crash.FirebaseCrash;
import com.platypii.baseline.Services;
import com.platypii.baseline.cloud.CloudData;
import java.io.File;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class Jump {

    // Jump info
    public final File logFile;

    private CloudData cloudData;

    public Jump(File logFile) {
        this.logFile = logFile;
    }

    private String cacheKey() {
        return "track." + logFile.getName();
    }

    /**
     * Returns cloud url info, if this track has been uploaded.
     * Will not initialize an upload, and will get cached, safe to use whenever.
     */
    public CloudData getCloudData() {
        if(cloudData != null) {
            return cloudData;
        } else {
            // Get from KV store
            final String trackUrl = Services.kv.getString(cacheKey());
            if(trackUrl != null) {
                final String trackKml = Services.kv.getString(cacheKey() + ".trackKml");
                cloudData = new CloudData(trackUrl, trackKml);
                return cloudData;
            } else {
                // Not in KV store, not uploaded
                return null;
            }
        }
    }

    public void setCloudData(CloudData cloudData) {
        Services.kv.put(cacheKey(), cloudData.trackUrl);
        Services.kv.put(cacheKey() + ".trackKml", cloudData.trackKml);
        this.cloudData = cloudData;
    }

    public String getName() {
        return logFile.getName()
                .replaceAll(".csv.gz", "")
                .replaceAll("_", " ")
                .replaceAll("-", ".");
    }

    public String getSize() {
        final long size = logFile.length() / 1024;
        return size + "kb";
    }

    /**
     * Parse date from filename
     */
    Date getDate() {
        final String dateString = getName().replaceAll("track ", "");
        final SimpleDateFormat format = new SimpleDateFormat("yyyy.MM.dd HH.mm.ss", Locale.US);
        try {
            return format.parse(dateString);
        } catch (ParseException e) {
            Log.e("Jump", "Failed to parse date from filename", e);
            FirebaseCrash.report(e);
            return null;
        }
    }

    /** Delete local track file */
    public boolean delete() {
        if(logFile.delete()) {
            cloudData = null;
            return true;
        } else {
            return false;
        }
    }

    @Override
    public String toString() {
        final SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
        return format.format(getDate());
    }

}
