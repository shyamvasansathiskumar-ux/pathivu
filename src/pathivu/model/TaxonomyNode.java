package pathivu.model;

/** One category inside one version of a framework, e.g. ASI03 in OWASP Agentic 2026. */
public record TaxonomyNode(int id, int versionId, Framework framework, String versionLabel,
                           String code, String name) {
    @Override public String toString() { return code + " " + name; }
}
