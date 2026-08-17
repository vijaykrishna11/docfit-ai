package com.docfitai.backend.reference.geoimport;

import java.math.BigDecimal;

/** One parsed geography reference row, source-agnostic (CLAUDE.md "Geography Import Pipeline"). */
public record GeographyRecord(String zipCode, String city, String stateCode, String county, BigDecimal latitude, BigDecimal longitude) {
}
