package pathivu.model;

/**
 * Yes / No / Unsure. Used for legal questions ("is personal data involved?").
 * UNSURE is deliberately treated like YES for deadlines: a clock keeps running
 * until a reviewer rules it out. Guessing "no" is how deadlines get missed.
 */
public enum TriState {
    YES, NO, UNSURE;

    /** True when a deadline should run: YES or UNSURE. */
    public boolean mightApply() { return this != NO; }
}
