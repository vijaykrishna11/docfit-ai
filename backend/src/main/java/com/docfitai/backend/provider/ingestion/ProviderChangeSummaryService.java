package com.docfitai.backend.provider.ingestion;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Operator-friendly summary of what a single import actually changed (CLAUDE.md "Change Event
 * Summary"): "12 locations added, 4 phone numbers changed, 2 provider names changed" -- not a raw
 * dump of every {@link ProviderChangeEvent} row, and never a quality judgment or clinical claim,
 * just a count per {@link ChangeType} for one {@code source_import_id}.
 */
@Service
public class ProviderChangeSummaryService {

    private final ProviderChangeEventRepository changeEventRepository;

    public ProviderChangeSummaryService(ProviderChangeEventRepository changeEventRepository) {
        this.changeEventRepository = changeEventRepository;
    }

    public ChangeSummary summarize(Long sourceImportId) {
        List<ProviderChangeEvent> events = changeEventRepository.findBySourceImportId(sourceImportId);
        Map<ChangeType, Integer> counts = new EnumMap<>(ChangeType.class);
        for (ProviderChangeEvent event : events) {
            counts.merge(event.getChangeType(), 1, Integer::sum);
        }
        return new ChangeSummary(sourceImportId, Map.copyOf(counts));
    }

    public record ChangeSummary(Long sourceImportId, Map<ChangeType, Integer> countsByType) {

        /** A short, human-readable line -- "12 locations added, 4 phone numbers changed, 2 provider names changed". Empty string if nothing changed. */
        public String toHumanSummary() {
            if (countsByType.isEmpty()) {
                return "No tracked changes";
            }
            StringBuilder sb = new StringBuilder();
            appendIfPresent(sb, ChangeType.LOCATION_ADDED, "location", "added");
            appendIfPresent(sb, ChangeType.PHONE_CHANGED, "phone number", "changed");
            appendIfPresent(sb, ChangeType.PROVIDER_NAME_CHANGED, "provider name", "changed");
            appendIfPresent(sb, ChangeType.ORGANIZATION_NAME_CHANGED, "organization name", "changed");
            appendIfPresent(sb, ChangeType.TAXONOMY_ADDED, "specialty", "added");
            return sb.toString();
        }

        private void appendIfPresent(StringBuilder sb, ChangeType type, String noun, String verb) {
            Integer count = countsByType.get(type);
            if (count == null || count == 0) {
                return;
            }
            if (!sb.isEmpty()) {
                sb.append(", ");
            }
            sb.append(count).append(' ').append(noun).append(count == 1 ? "" : "s").append(' ').append(verb);
        }
    }
}
