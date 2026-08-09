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
 * Неизменяемое представление {@code semantic/criteria-catalog.json} в памяти: канонический id
 * для каждого критерия и алиасы-синонимы, которые к нему ведут. Чистая Java (Jackson только
 * для парсинга), поэтому можно собрать прямо в тестах без контекста Spring.
 */
public record CriteriaCatalog(String version, Map<String, String> aliasToCanonicalId, Set<String> canonicalIds) {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static CriteriaCatalog parse(final InputStream in) throws IOException {
        final JsonNode root = MAPPER.readTree(in);
        final String version = root.path("version").asText();

        final Map<String, String> aliasToCanonicalId = new HashMap<>();
        final Set<String> canonicalIds = new LinkedHashSet<>();

        for (final JsonNode resource : root.path("resources")) {
            final String id = resource.path("id").asText();
            canonicalIds.add(id);
            aliasToCanonicalId.put(normalize(id), id);
            for (final JsonNode alias : resource.path("aliases")) {
                aliasToCanonicalId.put(normalize(alias.asText()), id);
            }
        }

        return new CriteriaCatalog(version, Map.copyOf(aliasToCanonicalId), Set.copyOf(canonicalIds));
    }

    /** Преобразует сырой (возможно, алиасный) ключ критерия в канонический id каталога. */
    public Optional<String> resolve(final String rawKey) {
        if (rawKey == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(aliasToCanonicalId.get(normalize(rawKey)));
    }

    public boolean isCanonical(final String id) {
        return canonicalIds.contains(id);
    }

    private static String normalize(final String key) {
        return key.trim().toLowerCase(Locale.ROOT);
    }
}
