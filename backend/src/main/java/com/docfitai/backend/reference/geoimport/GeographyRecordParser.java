package com.docfitai.backend.reference.geoimport;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Parses one CSV data row into a {@link GeographyRecord}. Pure/side-effect free (no I/O), matching
 * {@code ProviderCsvRecordParser}'s convention -- directly unit testable.
 *
 * <p>Expected header (order-independent by name): {@code zip_code,city,state_code,county,latitude,
 * longitude}. {@code county} is optional (CLAUDE.md "County": never inferred if the source
 * genuinely doesn't supply one). Deliberately simple comma-splitting, not full RFC 4180 quoted-
 * field parsing -- same rationale as the provider CSV importer (bounded, operator-prepared files,
 * not arbitrary untrusted uploads).
 */
public final class GeographyRecordParser {

    public static final List<String> EXPECTED_COLUMNS =
            List.of("zip_code", "city", "state_code", "county", "latitude", "longitude");

    private GeographyRecordParser() {
    }

    public static List<String> parseHeader(String headerLine) {
        List<String> columns = new ArrayList<>();
        for (String column : headerLine.split(",", -1)) {
            columns.add(column.trim().toLowerCase(Locale.ROOT));
        }
        if (!columns.containsAll(List.of("zip_code", "city", "state_code", "latitude", "longitude"))) {
            throw new IllegalArgumentException(
                    "Geography CSV header is missing a required column. Expected at least: zip_code, city, state_code, latitude, longitude");
        }
        return columns;
    }

    public static GeographyRecord parseRow(List<String> header, String line) {
        String[] fields = line.split(",", -1);
        if (fields.length != header.size()) {
            throw new IllegalArgumentException(
                    "Row has " + fields.length + " fields, expected " + header.size() + " matching the header");
        }
        Map<String, String> row = new HashMap<>();
        for (int i = 0; i < header.size(); i++) {
            row.put(header.get(i), fields[i].trim());
        }

        String zipCode = required(row, "zip_code");
        if (!zipCode.matches("\\d{5}")) {
            throw new IllegalArgumentException("Invalid zip_code (must be 5 digits): " + zipCode);
        }
        String city = required(row, "city");
        String stateCode = required(row, "state_code").toUpperCase(Locale.ROOT);
        if (stateCode.length() != 2) {
            throw new IllegalArgumentException("Invalid state_code (must be 2 letters): " + stateCode);
        }
        String county = blankToNull(row.get("county"));

        BigDecimal latitude = requiredDecimal(row, "latitude");
        if (latitude.compareTo(BigDecimal.valueOf(-90)) < 0 || latitude.compareTo(BigDecimal.valueOf(90)) > 0) {
            throw new IllegalArgumentException("Invalid latitude for ZIP " + zipCode + ": " + latitude);
        }
        BigDecimal longitude = requiredDecimal(row, "longitude");
        if (longitude.compareTo(BigDecimal.valueOf(-180)) < 0 || longitude.compareTo(BigDecimal.valueOf(180)) > 0) {
            throw new IllegalArgumentException("Invalid longitude for ZIP " + zipCode + ": " + longitude);
        }

        return new GeographyRecord(zipCode, city, stateCode, county, latitude, longitude);
    }

    private static String required(Map<String, String> row, String key) {
        String value = row.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing required column: " + key);
        }
        return value;
    }

    private static BigDecimal requiredDecimal(Map<String, String> row, String key) {
        String value = required(row, key);
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid decimal value for " + key + ": " + value);
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
