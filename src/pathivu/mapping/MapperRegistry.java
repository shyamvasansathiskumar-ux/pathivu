package pathivu.mapping;

import java.util.ArrayList;
import java.util.List;
import pathivu.model.Harm;
import pathivu.model.Incident;
import pathivu.model.MappingSuggestion;

/** Runs every mapper. Adding a framework = one new class + one line here. */
public class MapperRegistry {
    private final List<FrameworkMapper> mappers = List.of(
        new OwaspAgenticMapper(), new OwaspLlm2026Mapper(), new AtlasMapper(), new NistRmfMapper());

    public List<MappingSuggestion> suggestAll(Incident incident, List<Harm> harms) {
        Signals s = Signals.of(incident, harms);
        List<MappingSuggestion> out = new ArrayList<>();
        for (FrameworkMapper m : mappers) out.addAll(m.suggest(s));
        return out;
    }
}
