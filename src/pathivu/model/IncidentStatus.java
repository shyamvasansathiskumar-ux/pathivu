package pathivu.model;

/** Investigation status. Legal moves between these live in {@link pathivu.workflow.IncidentWorkflow}. */
public enum IncidentStatus { REPORTED, TRIAGED, ASSESSED, NOTICES_REVIEWED, CLOSED }
