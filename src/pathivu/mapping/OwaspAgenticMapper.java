package pathivu.mapping;

import java.util.ArrayList;
import java.util.List;
import pathivu.model.*;

/** OWASP Top 10 for Agentic Applications 2026 (ASI01–ASI10, released Dec 2025). */
public class OwaspAgenticMapper implements FrameworkMapper {
    @Override public Framework framework() { return Framework.OWASP_AGENTIC; }

    @Override public List<MappingSuggestion> suggest(Signals s) {
        List<MappingSuggestion> out = new ArrayList<>();
        if (!s.isAgent()) return out;
        if (s.promptInjection())
            out.add(new MappingSuggestion(framework(), "ASI01",
                "Instructions from content the agent read redirected its goal away from the task it was given.", Confidence.HIGH));
        if (s.destructiveAction() && s.noConfirmationStep())
            out.add(new MappingSuggestion(framework(), "ASI02",
                "A legitimate tool was used for a destructive action with no human confirmation step.", Confidence.MEDIUM));
        if (s.credentialBeyondTask())
            out.add(new MappingSuggestion(framework(), "ASI03",
                "The agent held credentials wider than its task needed, so a mistake could reach more systems.", Confidence.HIGH));
        if (s.hiddenContext())
            out.add(new MappingSuggestion(framework(), "ASI06",
                "Content in the agent's context or memory influenced its actions.", Confidence.LOW));
        return out;
    }
}
