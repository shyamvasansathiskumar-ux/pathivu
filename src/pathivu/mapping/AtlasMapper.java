package pathivu.mapping;

import java.util.ArrayList;
import java.util.List;
import pathivu.model.*;

/**
 * MITRE ATLAS catalogues ADVERSARY techniques. When nobody attacked, the
 * honest mapping is "no applicable technique". Pathivu records that as an
 * explicit NONE node, so "no mapping" is a decision someone made, not a gap.
 */
public class AtlasMapper implements FrameworkMapper {
    @Override public Framework framework() { return Framework.MITRE_ATLAS; }

    @Override public List<MappingSuggestion> suggest(Signals s) {
        List<MappingSuggestion> out = new ArrayList<>();
        if (s.adversary() == Adversary.NONE) {
            out.add(new MappingSuggestion(framework(), "NONE",
                "No adversary was involved. ATLAS lists attacker techniques, so none applies; recorded explicitly.", Confidence.HIGH));
            return out;
        }
        Confidence c = s.adversary() == Adversary.CONFIRMED ? Confidence.HIGH : Confidence.MEDIUM;
        if (s.promptInjection())
            out.add(new MappingSuggestion(framework(), "AML.T0051",
                "An attacker placed instructions in input the model processed (LLM prompt injection).", c));
        if (s.jailbreak())
            out.add(new MappingSuggestion(framework(), "AML.T0054",
                "An attacker bypassed the model's safety restrictions (jailbreak).", c));
        if (s.dataExposure())
            out.add(new MappingSuggestion(framework(), "AML.T0057",
                "An attacker got the model to reveal data it should not have (LLM data leakage).", Confidence.MEDIUM));
        if (s.poisoning())
            out.add(new MappingSuggestion(framework(), "AML.T0020",
                "Training or retrieval data was deliberately corrupted.", c));
        return out;
    }
}
