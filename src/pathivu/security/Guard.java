package pathivu.security;

import pathivu.model.AppUser;
import pathivu.model.Role;

/** Role checks in one place. The database repeats the important ones in triggers. */
public final class Guard {
    private Guard() { }

    public static void require(AppUser user, Role role, String action) {
        if (user == null || user.role() != role)
            throw new AccessDenied("Only a " + role.name().toLowerCase() + " can " + action + ".");
    }

    /** Reporters and reviewers can write. Auditors only read. */
    public static void requireWriter(AppUser user, String action) {
        if (user == null || user.role() == Role.AUDITOR)
            throw new AccessDenied("Auditors have read-only access, so they cannot " + action + ".");
    }
}
