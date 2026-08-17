package com.docfitai.backend.provider.nppes;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Shape shared by both {@code addresses} entries and {@code practiceLocations} entries. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record NppesAddress(
        @JsonProperty("address_purpose") String addressPurpose,
        @JsonProperty("address_1") String address1,
        @JsonProperty("address_2") String address2,
        String city,
        String state,
        @JsonProperty("postal_code") String postalCode,
        @JsonProperty("telephone_number") String telephoneNumber,
        @JsonProperty("fax_number") String faxNumber) {
}
