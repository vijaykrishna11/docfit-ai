package com.docfitai.backend.reference.geoimport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class GeographyRecordParserTest {

    private static final List<String> HEADER = GeographyRecordParser.parseHeader("zip_code,city,state_code,county,latitude,longitude");

    @Test
    void parsesAWellFormedRow() {
        GeographyRecord record = GeographyRecordParser.parseRow(HEADER, "90802,Long Beach,CA,Los Angeles,33.770000,-118.191000");

        assertThat(record.zipCode()).isEqualTo("90802");
        assertThat(record.city()).isEqualTo("Long Beach");
        assertThat(record.stateCode()).isEqualTo("CA");
        assertThat(record.county()).isEqualTo("Los Angeles");
        assertThat(record.latitude()).isEqualByComparingTo(new BigDecimal("33.770000"));
        assertThat(record.longitude()).isEqualByComparingTo(new BigDecimal("-118.191000"));
    }

    @Test
    void countyIsOptional() {
        GeographyRecord record = GeographyRecordParser.parseRow(HEADER, "90802,Long Beach,CA,,33.770000,-118.191000");
        assertThat(record.county()).isNull();
    }

    @Test
    void cityIsOptional() {
        // A real, legitimate LA County ZCTA can have no resolvable primary city (CLAUDE.md "City
        // Representation Limitations") -- never rejected, never fabricated.
        GeographyRecord record = GeographyRecordParser.parseRow(HEADER, "93553,,CA,Los Angeles,34.445239,-117.894868");
        assertThat(record.city()).isNull();
        assertThat(record.zipCode()).isEqualTo("93553");
    }

    @Test
    void rejectsAMalformedZipCode() {
        assertThatThrownBy(() -> GeographyRecordParser.parseRow(HEADER, "ABC12,Long Beach,CA,Los Angeles,33.77,-118.19"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid zip_code");
    }

    @Test
    void rejectsAnOutOfRangeLatitude() {
        assertThatThrownBy(() -> GeographyRecordParser.parseRow(HEADER, "90802,Long Beach,CA,Los Angeles,190.0,-118.19"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid latitude");
    }

    @Test
    void rejectsAnOutOfRangeLongitude() {
        assertThatThrownBy(() -> GeographyRecordParser.parseRow(HEADER, "90802,Long Beach,CA,Los Angeles,33.77,-190.0"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid longitude");
    }

    @Test
    void rejectsAMissingRequiredColumn() {
        assertThatThrownBy(() -> GeographyRecordParser.parseHeader("zip_code,city,latitude,longitude"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsARowWithTheWrongFieldCount() {
        assertThatThrownBy(() -> GeographyRecordParser.parseRow(HEADER, "90802,Long Beach,CA"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
