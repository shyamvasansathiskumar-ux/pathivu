package pathivu.mapping;

import java.util.ArrayList;
import java.util.List;
import pathivu.model.*;

/** OWASP Top 10 for LLM Applications 2026 (published 3 Aug 2026). Codes changed from 2025: e.g. Excessive Agency is now LLM03. */
public class OwaspLlm2026Mapper implements FrameworkMapper {
    @Override public Framework framework() { return Framework.OWASP_LLM; }

    @Override public List<MappingSuggestion> suggest(Signals s) {
        List<MappingSuggestion> out = new ArrayList<>();
        if (s.promptInjection())
            out.add(new MappingSuggestion(framework(), "LLM01",
                "Untrusted input changed the model's behaviour (prompt injection).", Confidence.HIGH));
        if (s.dataExposure() && s.personalData())
            out.add(new MappingSuggestion(framework(), "LLM02",
                "The system disclosed personal or sensitive information it should have withheld.", Confidence.MEDIUM));
        if (s.isAgent() && (s.credentialBeyondTask() || s.noConfirmationStep()))
            out.add(new MappingSuggestion(framework(), "LLM03",
                "The model could take consequential actions with too much permission or autonomy (excessive agency).", Confidence.HIGH));
        if (s.outputType() == OutputType.MISINFORMATION)
            out.add(new MappingSuggestion(framework(), "LLM07",
                "The model produced false information that people relied on.", Confidence.HIGH));
        if (s.hiddenContext())
            out.add(new MappingSuggestion(framework(), "LLM08",
                "Hidden context such as a system prompt or retrieved text was exposed or influential.", Confidence.MEDIUM));
        return out;
    }
}
