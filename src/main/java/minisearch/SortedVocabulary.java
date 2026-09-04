package minisearch;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

final class SortedVocabulary {
    private final List<String> terms;

    SortedVocabulary(Collection<String> vocabulary) {
        terms = new ArrayList<>(vocabulary);
        terms.sort(String::compareTo);
    }

    List<String> suggest(String prefix, int limit) {
        if (limit <= 0) {
            return List.of();
        }

        String normalizedPrefix = prefix.toLowerCase(Locale.ROOT);
        List<String> suggestions = new ArrayList<>();
        for (int index = lowerBound(normalizedPrefix);
                index < terms.size() && suggestions.size() < limit;
                index++) {
            String term = terms.get(index);
            if (!term.startsWith(normalizedPrefix)) {
                break;
            }
            suggestions.add(term);
        }
        return suggestions;
    }

    private int lowerBound(String prefix) {
        int low = 0;
        int high = terms.size();
        while (low < high) {
            int middle = low + (high - low) / 2;
            if (terms.get(middle).compareTo(prefix) < 0) {
                low = middle + 1;
            } else {
                high = middle;
            }
        }
        return low;
    }
}
