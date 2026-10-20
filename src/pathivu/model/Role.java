package pathivu.model;

/**
 * What a user may do.
 * REPORTER files incidents, harms, evidence and suggests mappings.
 * REVIEWER triages, confirms mappings, rules clocks out and approves notices.
 * AUDITOR only reads, and can verify the audit chain.
 */
public enum Role { REPORTER, REVIEWER, AUDITOR }
