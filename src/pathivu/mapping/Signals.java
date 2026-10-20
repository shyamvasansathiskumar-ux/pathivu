package pathivu.mapping;

import java.util.List;
import java.util.Locale;
import pathivu.model.*;

/**
 * Facts the mappers look at, worked out once. Keeping them here means every
 * mapper reads the incident the same way.
 */
public record Signals(boolean promptInjection, boolean jailbreak, boolean poisoning, boolean hiddenContext,
                      boolean personalData, boolean dataLossOrOutage, boolean dataExposure,
                      boolean destructiveAction, boolean credentialBeyondTask, boolean noConfirmationStep,
                      boolean isAgent, OutputType outputType, Adversary adversary) {

    public static Signals of(Incident i, List<Harm> harms) {
        String text = (i.getTitle() + " " + i.getDescription() + " " + i.specificsSummary()).toLowerCase(Locale.ROOT);
        var tags = i.getTags();
        boolean injection = tags.contains("prompt-injection") || text.contains("prompt injection")
                         || text.contains("injected instruction") || text.contains("ignore previous");
        boolean exposure = harms.stream().anyMatch(h -> h.category() == HarmCategory.DATA_EXPOSURE)
                        || i instanceof DataIncident;
        boolean lossOrOutage = harms.stream().anyMatch(h -> h.category() == HarmCategory.DATA_LOSS
                                                         || h.category() == HarmCategory.SERVICE_OUTAGE);
        boolean destructive = text.matches(".*\\b(delete[sd]?|deleting|drop(ped)?|wiped?|rm -rf|truncated?|destroy(ed)?)\\b.*")
                           || lossOrOutage;
        boolean agent = i instanceof AgentIncident;
        AgentIncident a = agent ? (AgentIncident) i : null;
        return new Signals(
            injection,
            tags.contains("jailbreak") || text.contains("jailbreak"),
            tags.contains("poisoning") || text.contains("poisoned"),
            tags.contains("system-prompt") || text.contains("system prompt"),
            harms.stream().anyMatch(h -> h.personalData() == TriState.YES) || (i instanceof DataIncident),
            lossOrOutage, exposure, destructive,
            agent && a.isCredentialBeyondTask(),
            agent && !a.isHadConfirmationStep(),
            agent,
            i instanceof ModelHarmIncident m ? m.getOutputType() : null,
            i.getAdversary());
    }
}
