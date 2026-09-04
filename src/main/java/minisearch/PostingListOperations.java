package minisearch;

import java.util.ArrayList;
import java.util.List;

final class PostingListOperations {
    private PostingListOperations() {
    }

    static List<Integer> intersection(List<Posting> first, List<Posting> second) {
        List<Integer> result = new ArrayList<>();
        int firstIndex = 0;
        int secondIndex = 0;
        while (firstIndex < first.size() && secondIndex < second.size()) {
            int firstDocumentId = first.get(firstIndex).documentId();
            int secondDocumentId = second.get(secondIndex).documentId();
            if (firstDocumentId == secondDocumentId) {
                result.add(firstDocumentId);
                firstIndex++;
                secondIndex++;
            } else if (firstDocumentId < secondDocumentId) {
                firstIndex++;
            } else {
                secondIndex++;
            }
        }
        return result;
    }

    static List<Integer> union(List<Posting> first, List<Posting> second) {
        List<Integer> result = new ArrayList<>();
        int firstIndex = 0;
        int secondIndex = 0;
        while (firstIndex < first.size() && secondIndex < second.size()) {
            int firstDocumentId = first.get(firstIndex).documentId();
            int secondDocumentId = second.get(secondIndex).documentId();
            if (firstDocumentId == secondDocumentId) {
                result.add(firstDocumentId);
                firstIndex++;
                secondIndex++;
            } else if (firstDocumentId < secondDocumentId) {
                result.add(firstDocumentId);
                firstIndex++;
            } else {
                result.add(secondDocumentId);
                secondIndex++;
            }
        }
        while (firstIndex < first.size()) {
            result.add(first.get(firstIndex++).documentId());
        }
        while (secondIndex < second.size()) {
            result.add(second.get(secondIndex++).documentId());
        }
        return result;
    }

    static List<Integer> difference(List<Posting> first, List<Posting> second) {
        List<Integer> result = new ArrayList<>();
        int firstIndex = 0;
        int secondIndex = 0;
        while (firstIndex < first.size() && secondIndex < second.size()) {
            int firstDocumentId = first.get(firstIndex).documentId();
            int secondDocumentId = second.get(secondIndex).documentId();
            if (firstDocumentId == secondDocumentId) {
                firstIndex++;
                secondIndex++;
            } else if (firstDocumentId < secondDocumentId) {
                result.add(firstDocumentId);
                firstIndex++;
            } else {
                secondIndex++;
            }
        }
        while (firstIndex < first.size()) {
            result.add(first.get(firstIndex++).documentId());
        }
        return result;
    }
}
