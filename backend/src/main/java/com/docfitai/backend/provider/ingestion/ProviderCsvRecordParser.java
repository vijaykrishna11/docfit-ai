package com.docfitai.backend.provider.ingestion;

import com.docfitai.backend.provider.CoordinatePrecision;
import com.docfitai.backend.provider.ProviderEntityType;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses one CSV data row into a {@link ProviderImportRecord}. Pure/side-effect free (no I/O) so
 * it can be unit tested directly, matching {@code NppesProviderMapper}'s convention.
 *
 * <p>Expected header (order-independent by name):
 * {@code npi,entity_type,first_name,last_name,organization_name,address_line_1,address_line_2,
 * city,state_code,postal_code,phone,fax,latitude,longitude,taxonomy_codes}. One row is one
 * (provider, location) pair -- a provider with multiple offices is represented by multiple rows
 * sharing the same NPI, each producing an additional location via the same idempotent upsert path
 * NPPES import uses (CLAUDE.md 5, 19-20). {@code taxonomy_codes} is a semicolon-separated list;
 * the first code is treated as primary.
 *
 * <p>Deliberately simple comma-splitting, not full RFC 4180 quoted-field parsing -- this importer
 * is for bounded, operator-prepared files (CLAUDE.md 29-30), not arbitrary untrusted uploads, so a
 * full CSV grammar would be more complexity than the use case justifies. A field containing a
 * literal comma is not supported; such a row fails clearly (caught and counted by the caller)
 * rather than being silently misparsed.
 */
public final class ProviderCsvRecordParser {

    public static final List<String> EXPECTED_COLUMNS = List.of(
            "npi", "entity_type", "first_name", "last_name", "organization_name", "address_line_1", "address_line_2",
            "city", "state_code", "postal_code", "phone", "fax", "latitude", "longitude", "taxonomy_codes");

    private ProviderCsvRecordParser() {
    }

    public static List<String> parseHeader(String headerLine) {
        List<String> columns = new ArrayList<>();
        for (String column : headerLine.split(",", -1)) {
            columns.add(column.trim().toLowerCase(java.util.Locale.ROOT));
        }
        return columns;
    }

    public static ProviderImportRecord parseRow(List<String> header, String line) {
        String[] fields = line.split(",", -1);
        if (fields.length != header.size()) {
            throw new IllegalArgumentException(
                    "Row has " + fields.length + " fields, expected " + header.size() + " matching the header");
        }
        java.util.Map<String, String> row = new java.util.HashMap<>();
        for (int i = 0; i < header.size(); i++) {
            row.put(header.get(i), fields[i].trim());
        }

        String npi = required(row, "npi");
        ProviderEntityType entityType = "ORGANIZATION".equalsIgnoreCase(row.get("entity_type"))
                ? ProviderEntityType.ORGANIZATION
                : ProviderEntityType.INDIVIDUAL;
        ProviderIdentityRecord identity = new ProviderIdentityRecord(
                npi, entityType, blankToNull(row.get("first_name")), blankToNull(row.get("last_name")), blankToNull(row.get("organization_name")));

        BigDecimal latitude = parseDecimal(row.get("latitude"));
        BigDecimal longitude = parseDecimal(row.get("longitude"));
        CoordinatePrecision precision = latitude != null && longitude != null ? CoordinatePrecision.ADDRESS_GEOCODE : CoordinatePrecision.UNKNOWN;
        ProviderLocationRecord location = new ProviderLocationRecord(
                "LOCATION",
                required(row, "address_line_1"),
                blankToNull(row.get("address_line_2")),
                required(row, "city"),
                required(row, "state_code"),
                required(row, "postal_code"),
                blankToNull(row.get("phone")),
                blankToNull(row.get("fax")),
                latitude,
                longitude,
                precision);

        List<ProviderTaxonomyRecord> taxonomies = new ArrayList<>();
        String taxonomyCodes = row.get("taxonomy_codes");
        if (taxonomyCodes != null && !taxonomyCodes.isBlank()) {
            String[] codes = taxonomyCodes.split(";");
            for (int i = 0; i < codes.length; i++) {
                String code = codes[i].trim();
                if (!code.isEmpty()) {
                    taxonomies.add(new ProviderTaxonomyRecord(code, i == 0));
                }
            }
        }
        if (taxonomies.isEmpty()) {
            throw new IllegalArgumentException("Row for NPI " + npi + " has no taxonomy_codes");
        }

        return new ProviderImportRecord(identity, List.of(location), taxonomies);
    }

    private static String required(java.util.Map<String, String> row, String key) {
        String value = row.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing required column: " + key);
        }
        return value;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static BigDecimal parseDecimal(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
