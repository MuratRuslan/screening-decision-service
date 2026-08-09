package kg.tunduk.cvscan.screening.semantic;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Immutable, in-memory view of {@code semantic/criteria-catalog.json}: a canonical id per
 * criterion plus the synonym aliases that resolve to it. Pure Java (only Jackson for
 * parsing) so it can be built directly in tests without a Spring context.
 */
public record CriteriaCatalog(String version, Map<String, String> aliasToCanonicalId, Set<String> canonicalIds) {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static CriteriaCatalog parse(InputStream in) throws IOException {
        JsonNode root = MAPPER.readTree(in);
        String version = root.path("version").asText();

        Map<String, String> aliasToCanonicalId = new HashMap<>();
        Set<String> canonicalIds = new LinkedHashSet<>();

        for (JsonNode resource : root.path("resources")) {
            String id = resource.path("id").asText();
            canonicalIds.add(id);
            aliasToCanonicalId.put(normalize(id), id);
            for (JsonNode alias : resource.path("aliases")) {
                aliasToCanonicalId.put(normalize(alias.asText()), id);
            }
        }

        return new CriteriaCatalog(version, Map.copyOf(aliasToCanonicalId), Set.copyOf(canonicalIds));
    }

    /** Resolves a raw (possibly alias) criterion key to its canonical catalog id. */
    public Optional<String> resolve(String rawKey) {
        if (rawKey == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(aliasToCanonicalId.get(normalize(rawKey)));
    }

    public boolean isCanonical(String id) {
        return canonicalIds.contains(id);
    }

    private static String normalize(String key) {
        return key.trim().toLowerCase(Locale.ROOT);
    }
}
