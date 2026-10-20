package pathivu.mapping;

import java.util.ArrayList;
import java.util.List;
import pathivu.model.*;

/** NIST AI RMF 1.0: which core function failed or now needs action. */
public class NistRmfMapper implements FrameworkMapper {
    @Override public Framework framework() { return Framework.NIST_AI_RMF; }

    @Override public List<MappingSuggestion> suggest(Signals s) {
        List<MappingSuggestion> out = new ArrayList<>();
        if (s.credentialBeyondTask() || s.noConfirmationStep())
            out.add(new MappingSuggestion(framework(), "GOVERN",
                "Access and approval policies for the AI system did not limit what it could do.", Confidence.MEDIUM));
        if (s.personalData())
            out.add(new MappingSuggestion(framework(), "MAP",
                "The risk to personal data in this context was not identified before deployment.", Confidence.LOW));
        if (s.outputType() == OutputType.BIASED_DECISION || s.outputType() == OutputType.MISINFORMATION)
            out.add(new MappingSuggestion(framework(), "MEASURE",
                "Output quality or fairness was not measured well enough to catch this.", Confidence.MEDIUM));
        out.add(new MappingSuggestion(framework(), "MANAGE",
            "The incident needs response, recovery and a recorded fix (risk treatment).", Confidence.HIGH));
        return out;
    }
}
