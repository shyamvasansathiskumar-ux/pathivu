package pathivu.security;

/** Thrown when someone tries something their role does not allow. */
public class AccessDenied extends RuntimeException {
    public AccessDenied(String message) { super(message); }
}
