package pathivu.mapping;

import java.util.List;
import pathivu.model.Framework;
import pathivu.model.MappingSuggestion;

/**
 * One mapper per framework (interface + four implementations). A mapper only
 * SUGGESTS. Nothing it says counts until a reviewer confirms it.
 */
public interface FrameworkMapper {
    Framework framework();
    List<MappingSuggestion> suggest(Signals s);
}
