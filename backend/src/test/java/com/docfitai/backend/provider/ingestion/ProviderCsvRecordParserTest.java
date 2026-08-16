package com.docfitai.backend.provider.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.docfitai.backend.provider.ProviderEntityType;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProviderCsvRecordParserTest {

    private static final String HEADER =
            "npi,entity_type,first_name,last_name,organization_name,address_line_1,address_line_2,city,state_code,postal_code,phone,fax,latitude,longitude,taxonomy_codes";

    @Test
    void parsesAnIndividualRowWithPrimaryAndSecondaryTaxonomy() {
        List<String> header = ProviderCsvRecordParser.parseHeader(HEADER);
        String row = "1111111111,INDIVIDUAL,Jane,Doe,,1 Test Ave,,Long Beach,CA,90802,562-555-0001,,33.77,-118.19,207RC0000X;207RI0011X";

        ProviderImportRecord record = ProviderCsvRecordParser.parseRow(header, row);

        assertThat(record.identity().npiNumber()).isEqualTo("1111111111");
        assertThat(record.identity().entityType()).isEqualTo(ProviderEntityType.INDIVIDUAL);
        assertThat(record.identity().firstName()).isEqualTo("Jane");
        assertThat(record.locations()).hasSize(1);
        assertThat(record.locations().get(0).addressLine1()).isEqualTo("1 Test Ave");
        assertThat(record.taxonomies()).hasSize(2);
        assertThat(record.taxonomies().get(0).primary()).isTrue();
        assertThat(record.taxonomies().get(1).primary()).isFalse();
    }

    @Test
    void parsesAnOrganizationRow() {
        List<String> header = ProviderCsvRecordParser.parseHeader(HEADER);
        String row = "2222222222,ORGANIZATION,,,Test Medical Group,1 Group Way,,Long Beach,CA,90802,,,,,207RC0000X";

        ProviderImportRecord record = ProviderCsvRecordParser.parseRow(header, row);

        assertThat(record.identity().entityType()).isEqualTo(ProviderEntityType.ORGANIZATION);
        assertThat(record.identity().organizationName()).isEqualTo("Test Medical Group");
        assertThat(record.identity().firstName()).isNull();
    }

    @Test
    void rejectsARowMissingRequiredFields() {
        List<String> header = ProviderCsvRecordParser.parseHeader(HEADER);
        String row = "3333333333,INDIVIDUAL,Jane,Doe,,,,Long Beach,CA,90802,,,,,207RC0000X";

        assertThatThrownBy(() -> ProviderCsvRecordParser.parseRow(header, row)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsARowWithNoTaxonomyCodes() {
        List<String> header = ProviderCsvRecordParser.parseHeader(HEADER);
        String row = "4444444444,INDIVIDUAL,Jane,Doe,,1 Test Ave,,Long Beach,CA,90802,,,,,";

        assertThatThrownBy(() -> ProviderCsvRecordParser.parseRow(header, row)).isInstanceOf(IllegalArgumentException.class);
    }
}
