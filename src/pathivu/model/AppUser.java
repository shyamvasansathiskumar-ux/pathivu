package pathivu.model;

/** A logged-in person. Immutable: once loaded it never changes. */
public record AppUser(int id, String username, String fullName, Role role) {
    public boolean is(Role r) { return role == r; }
    @Override public String toString() { return fullName + " (" + role + ")"; }
}
