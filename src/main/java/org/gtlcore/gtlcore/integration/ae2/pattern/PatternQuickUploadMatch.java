package org.gtlcore.gtlcore.integration.ae2.pattern;

import java.util.List;

public record PatternQuickUploadMatch<T>(Status status, List<T> candidates) {

    public enum Status {
        NONE,
        UNIQUE,
        AMBIGUOUS
    }

    public static <T> PatternQuickUploadMatch<T> select(List<T> candidates) {
        List<T> safeCandidates = List.copyOf(candidates);
        return switch (safeCandidates.size()) {
            case 0 -> new PatternQuickUploadMatch<>(Status.NONE, safeCandidates);
            case 1 -> new PatternQuickUploadMatch<>(Status.UNIQUE, safeCandidates);
            default -> new PatternQuickUploadMatch<>(Status.AMBIGUOUS, safeCandidates);
        };
    }

    public T uniqueCandidate() {
        if (status != Status.UNIQUE) {
            throw new IllegalStateException("Quick upload match is not unique");
        }
        return candidates.get(0);
    }
}
