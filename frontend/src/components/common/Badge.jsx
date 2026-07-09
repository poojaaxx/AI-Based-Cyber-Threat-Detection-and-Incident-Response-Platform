const SEVERITY_STYLES = {
  LOW: 'bg-cg-info/15 text-cg-info',
  MEDIUM: 'bg-cg-warning/15 text-cg-warning',
  HIGH: 'bg-orange-500/15 text-orange-400',
  CRITICAL: 'bg-cg-danger/15 text-cg-danger',
};

const STATUS_STYLES = {
  DETECTED: 'bg-cg-warning/15 text-cg-warning',
  ANALYZING: 'bg-cg-info/15 text-cg-info',
  MITIGATED: 'bg-cg-success/15 text-cg-success',
  FALSE_POSITIVE: 'bg-slate-500/15 text-slate-400',
  IGNORED: 'bg-slate-500/15 text-slate-400',
  OPEN: 'bg-cg-warning/15 text-cg-warning',
  ASSIGNED: 'bg-cg-info/15 text-cg-info',
  IN_PROGRESS: 'bg-cg-info/15 text-cg-info',
  RESOLVED: 'bg-cg-success/15 text-cg-success',
  CLOSED: 'bg-slate-500/15 text-slate-400',
  ACTIVE: 'bg-cg-success/15 text-cg-success',
  DISABLED: 'bg-slate-500/15 text-slate-400',
  LOCKED: 'bg-cg-danger/15 text-cg-danger',
  // Generic states, for anything beyond the domain-specific Threat/Incident/User statuses above.
  SUCCESS: 'bg-cg-success/15 text-cg-success',
  ERROR: 'bg-cg-danger/15 text-cg-danger',
  WARNING: 'bg-cg-warning/15 text-cg-warning',
  ONLINE: 'bg-cg-success/15 text-cg-success',
  UP: 'bg-cg-success/15 text-cg-success',
  OFFLINE: 'bg-slate-500/15 text-slate-400',
  DOWN: 'bg-cg-danger/15 text-cg-danger',
};

export function SeverityBadge({ severity }) {
  return (
    <span className={`cg-badge ${SEVERITY_STYLES[severity] || SEVERITY_STYLES.LOW}`}>
      {severity}
    </span>
  );
}

export function StatusBadge({ status }) {
  return (
    <span className={`cg-badge ${STATUS_STYLES[status] || 'bg-slate-500/15 text-slate-400'}`}>
      {status?.replace('_', ' ')}
    </span>
  );
}

const RESPONSE_MODE_STYLES = {
  ADAPTIVE_POLICY: 'bg-cg-accent/15 text-cg-accent',
  STATIC_PLAYBOOK: 'bg-slate-500/15 text-slate-400',
};

const RESPONSE_MODE_LABELS = {
  ADAPTIVE_POLICY: 'Adaptive Policy',
  STATIC_PLAYBOOK: 'Static Playbook',
};

/**
 * Distinguishes which response-selection code path produced a given
 * ResponseAction, purely from its persisted `details` text - the RL-based
 * autoRespondAdaptive() prefixes its log entries with "Adaptive policy
 * (state=...)", while the static autoRespond() playbook uses "Automated
 * block/disable" or "Automatically generated incident". Returns null (no
 * badge rendered) when the text doesn't disambiguate - e.g. quarantine/notify
 * actions share identical wording in both code paths, so guessing would be
 * dishonest.
 */
export function detectResponseMode(detailsText) {
  if (!detailsText) return null;
  if (detailsText.startsWith('Adaptive policy')) return 'ADAPTIVE_POLICY';
  if (
    detailsText.startsWith('Automated block') ||
    detailsText.startsWith('Automated disable') ||
    detailsText.startsWith('Automatically generated incident')
  ) {
    return 'STATIC_PLAYBOOK';
  }
  return null;
}

export function ResponseModeBadge({ mode }) {
  if (!mode || !RESPONSE_MODE_LABELS[mode]) return null;
  return (
    <span className={`cg-badge ${RESPONSE_MODE_STYLES[mode]}`}>
      {RESPONSE_MODE_LABELS[mode]}
    </span>
  );
}
