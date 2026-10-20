package pathivu.model;

/** The four frameworks Pathivu maps incidents to. Matches taxonomy_version.framework. */
public enum Framework {
    OWASP_AGENTIC("OWASP Agentic Top 10"),
    OWASP_LLM("OWASP LLM Top 10"),
    MITRE_ATLAS("MITRE ATLAS"),
    NIST_AI_RMF("NIST AI RMF");

    private final String label;
    Framework(String label) { this.label = label; }
    public String label() { return label; }
}
