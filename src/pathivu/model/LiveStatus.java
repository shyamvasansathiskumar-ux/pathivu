package pathivu.model;

/**
 * What a person sees. OVERDUE and MET_LATE are never stored: they are worked
 * out from the stored state and the current time every time someone looks
 * (same logic as the view v_clock_status), so no background job can forget.
 */
public enum LiveStatus { RUNNING, OVERDUE, MET, MET_LATE, RULED_OUT }
