package com.platypii.baseline.tracks;

import com.platypii.baseline.altimeter.BaroAltimeter;
import com.platypii.baseline.util.CSVHeader;
import com.platypii.baseline.util.kalman.Filter;
import com.platypii.baseline.util.kalman.FilterKalman;
import com.platypii.baseline.measurements.MLocation;

import android.support.annotation.NonNull;
import android.util.Log;
import java.io.BufferedReader;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;

import static com.platypii.baseline.util.CSVParse.getColumnDate;
import static com.platypii.baseline.util.CSVParse.getColumnDouble;
import static com.platypii.baseline.util.CSVParse.getColumnLong;

/**
 * Parse location data from track file
 */
public class TrackFileData {
    private static final String TAG = "TrackFileData";

    @NonNull
    public static List<MLocation> getTrackData(@NonNull File trackFile) {
        // Try and parse file
        final List<MLocation> trackData = readTrackFile(trackFile);
        // Trim plane and ground
        return autoTrim(trackData);
    }

    /**
     * Load track data from file into location data
     */
    @NonNull
    private static List<MLocation> readTrackFile(File trackFile) {
        // Read file line by line
        // TODO minsdk19: InputStreamReader(,StandardCharsets.UTF_8)
        if (trackFile.getName().endsWith(".gz")) {
            // GZipped track file
            try (BufferedReader br = new BufferedReader(new InputStreamReader(new GZIPInputStream(new FileInputStream(trackFile))))) {
                return readTrackReader(br);
            } catch (EOFException e) {
                // Still error but less verbose
                Log.e(TAG, "Premature end of gzip track file " + trackFile + "\n" + e);
            } catch (IOException e) {
                Log.e(TAG, "Error reading track data from " + trackFile, e);
            }
        } else {
            // Uncompressed CSV file
            try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(trackFile)))) {
                return readTrackReader(br);
            } catch (IOException e) {
                Log.e(TAG, "Error reading track data from " + trackFile, e);
            }
        }
        return new ArrayList<>();
    }

    @NonNull
    private static List<MLocation> readTrackReader(BufferedReader br) throws IOException {
        // Altitude kalman filters
        final Filter baroAltitudeFilter = new FilterKalman();
        final Filter gpsAltitudeFilter = new FilterKalman();

        long baroLastNano = -1L;
        long gpsLastMillis = -1L;

        double baroOffset = 0;

        final List<MLocation> data = new ArrayList<>();

        // Parse header column
        String line = br.readLine();
        final CSVHeader columns = new CSVHeader(line);
        // Add column aliases
        columns.addMapping("timeMillis", "millis");
        // Handle old files that were not FlySight compatible
        columns.addMapping("latitude", "lat");
        columns.addMapping("longitude", "lon");
        columns.addMapping("altitude_gps", "hMSL");

        // Parse data rows
        while ((line = br.readLine()) != null) {
            final String[] row = line.split(",");
            final Integer sensorIndex = columns.get("sensor");
            if (sensorIndex == null) {
                // FlySight
                final long millis = getColumnDate(row, columns, "time");
                if (millis > 0) {
                    final double lat = getColumnDouble(row, columns, "lat");
                    final double lon = getColumnDouble(row, columns, "lon");
                    final double alt_gps = getColumnDouble(row, columns, "hMSL");
                    final double climb = -getColumnDouble(row, columns, "velD");
                    final double vN = getColumnDouble(row, columns, "velN");
                    final double vE = getColumnDouble(row, columns, "velE");
                    if (!Double.isNaN(lat) && !Double.isNaN(lon)) {
                        final MLocation loc = new MLocation(millis, lat, lon, alt_gps, climb, vN, vE, Float.NaN, Float.NaN, Float.NaN, Float.NaN, 0, 0);
                        data.add(loc);
                    }
                }
            } else if (row[sensorIndex].equals("gps")) {
                // BASEline GPS measurement
                final long millis = getColumnLong(row, columns, "millis");
                final double lat = getColumnDouble(row, columns, "lat");
                final double lon = getColumnDouble(row, columns, "lon");
                final double alt_gps = getColumnDouble(row, columns, "hMSL");
                final double vN = getColumnDouble(row, columns, "velN");
                final double vE = getColumnDouble(row, columns, "velE");
                // Update gps altitude filter
                if (gpsLastMillis < 0) {
                    gpsAltitudeFilter.update(alt_gps, 0);
                } else {
                    final double dt = (millis - gpsLastMillis) * 0.001;
                    gpsAltitudeFilter.update(alt_gps, dt);
                }
                gpsLastMillis = millis;
                // Climb rate from baro or gps
                double climb = baroAltitudeFilter.v();
                if (baroLastNano < 0 || Double.isNaN(climb)) {
                    climb = gpsAltitudeFilter.v();
                }
                if (!Double.isNaN(lat) && !Double.isNaN(lon)) {
                    final MLocation loc = new MLocation(millis, lat, lon, alt_gps, climb, vN, vE, Float.NaN, Float.NaN, Float.NaN, Float.NaN, 0, 0);
                    data.add(loc);
                }
            } else if (row[sensorIndex].equals("alt")) {
                // BASEline alti measurement
                final long nano = getColumnLong(row, columns, "nano");
                final double pressure = getColumnDouble(row, columns, "pressure");
                final double pressureAltitude = BaroAltimeter.pressureToAltitude(pressure);
                if (baroLastNano < 0) {
                    baroAltitudeFilter.update(pressureAltitude, 0);
                } else {
                    final double dt = (nano - baroLastNano) * 1E-9;
                    baroAltitudeFilter.update(pressureAltitude, dt);
                }
                baroLastNano = nano;
            }
        }

        return data;
    }

    /**
     * Trim plane ride and ground from track data
     */
    @NonNull
    private static List<MLocation> autoTrim(@NonNull List<MLocation> points) {
        // Margin size is the number of data points on either side of the jump
        // TODO: Use time instead of samples
        final int margin_size = 50;
        final int n = points.size();
        // Scan data
        int index_start = 0;
        int index_end = n;
        for (int i = 0; i < n; i++) {
            final MLocation point = points.get(i);
            if (index_start == 0 && point.climb < -4) {
                index_start = i;
            }
            if (point.climb < -2.5 && index_start < i) {
                index_end = i;
            }
        }
        // Conform to list bounds
        index_start = index_start - margin_size < 0 ? 0 : index_start - margin_size;
        index_end = index_end + margin_size > n ? n : index_end + margin_size;
        return points.subList(index_start, index_end);
    }

}
