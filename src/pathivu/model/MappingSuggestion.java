package pathivu.model;

/** What a mapper proposes, before it is saved. Every suggestion must say why. */
public record MappingSuggestion(Framework framework, String code, String rationale, Confidence confidence) {
    public MappingSuggestion {
        if (rationale == null || rationale.trim().length() < 15)
            throw new IllegalArgumentException("a mapping needs a rationale of at least 15 characters");
    }
}
