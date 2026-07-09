import {
  Brain, ShieldAlert, Ban, KeyRound, ShieldCheck, Flame, Activity, Search,
  Unplug, Bell, FileWarning, Sparkles, Microscope,
} from 'lucide-react';

function riskColor(score) {
  if (score >= 75) return 'text-cg-danger';
  if (score >= 50) return 'text-orange-400';
  if (score >= 25) return 'text-cg-warning';
  return 'text-cg-info';
}

const RECOMMENDATION_ICONS = {
  'Block Source IP': Ban,
  'Reset Credentials': KeyRound,
  'Enable MFA': ShieldCheck,
  'Review Firewall Rules': Flame,
  'Increase Monitoring': Activity,
  'Investigate Lateral Movement': Search,
  'Isolate Endpoint': Unplug,
  'Notify Security Team': Bell,
  'Create Incident': FileWarning,
};

/**
 * Renders a ThreatExplanationResponse (risk score, confidence, reasoning,
 * contributing-factor bars, MITRE ATT&CK mapping). Shared by ThreatDetail.jsx
 * and the Threat Simulation Lab so both render the exact same Explainable AI
 * output identically instead of maintaining two copies of this markup.
 */
export default function ExplainAiPanel({ explanation }) {
  if (!explanation) return null;

  return (
    <>
      <div className="cg-card space-y-4">
        <div className="flex items-center gap-2">
          <Brain size={18} className="text-cg-accent" />
          <h3 className="text-base font-semibold text-slate-100">Explain AI</h3>
        </div>

        <div className="flex items-center gap-6 flex-wrap">
          <div>
            <p className="text-xs text-slate-400 uppercase">Risk Score</p>
            <p className={`text-2xl font-bold ${riskColor(explanation.riskScore)}`}>
              {Number(explanation.riskScore).toFixed(0)}<span className="text-sm text-slate-500">/100</span>
            </p>
          </div>
          <div>
            <p className="text-xs text-slate-400 uppercase">Confidence</p>
            <p className="text-2xl font-bold text-slate-100">{Number(explanation.confidenceScore).toFixed(0)}%</p>
          </div>
        </div>

        <div>
          <p className="text-xs text-slate-400 uppercase mb-1">Reasoning</p>
          <p className="text-sm text-slate-300 bg-cg-surface-alt border border-cg-border rounded-md p-3">
            {explanation.reasoning}
          </p>
        </div>

        <div>
          <p className="text-xs text-slate-400 uppercase mb-2">Contributing Factors</p>
          <div className="space-y-2">
            {explanation.contributingFactors?.map((f) => (
              <div key={f.feature} className="text-sm">
                <div className="flex justify-between mb-1">
                  <span className="text-slate-300">{f.feature}: <span className="font-mono text-slate-400">{f.value}</span></span>
                  <span className="text-slate-500 text-xs">{f.importance}% weight</span>
                </div>
                <div className="h-1.5 bg-cg-surface-alt rounded-full overflow-hidden">
                  <div className="h-full bg-cg-accent rounded-full" style={{ width: `${Math.min(100, f.importance)}%` }} />
                </div>
                <p className="text-xs text-slate-500 mt-1">{f.description}</p>
              </div>
            ))}
          </div>
        </div>
      </div>

      {explanation.shapExplanation?.length > 0 && (
        <div className="cg-card space-y-3">
          <div className="flex items-center gap-2">
            <Microscope size={18} className="text-cg-accent" />
            <h3 className="text-base font-semibold text-slate-100">SHAP Explanation (per-instance)</h3>
          </div>
          <p className="text-xs text-slate-500">
            Computed for this specific request via shap.TreeExplainer, so - unlike the model-wide
            ranking above - both the ranking and magnitudes below are unique to this detection.
          </p>
          <div className="space-y-2">
            {explanation.shapExplanation.map((f) => (
              <div key={f.feature} className="text-sm">
                <div className="flex justify-between mb-1">
                  <span className="text-slate-300">{f.feature}: <span className="font-mono text-slate-400">{f.value}</span></span>
                  <span className={`text-xs font-mono ${f.importance >= 0 ? 'text-cg-success' : 'text-cg-danger'}`}>
                    {f.importance >= 0 ? '+' : ''}{f.importance}
                  </span>
                </div>
                <p className="text-xs text-slate-500 mt-1">{f.description}</p>
              </div>
            ))}
          </div>
        </div>
      )}

      {explanation.recommendations?.length > 0 && (
        <div className="cg-card space-y-3">
          <div className="flex items-center gap-2">
            <Sparkles size={18} className="text-cg-accent" />
            <h3 className="text-base font-semibold text-slate-100">AI Recommendations</h3>
          </div>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
            {explanation.recommendations.map((rec) => {
              const Icon = RECOMMENDATION_ICONS[rec] || Sparkles;
              return (
                <div key={rec} className="flex items-center gap-3 bg-cg-surface-alt border border-cg-border rounded-md px-3 py-2.5 hover:border-cg-accent/40 transition">
                  <div className="w-8 h-8 rounded-full bg-cg-accent/10 text-cg-accent flex items-center justify-center shrink-0">
                    <Icon size={16} />
                  </div>
                  <span className="text-sm text-slate-200 font-medium">{rec}</span>
                </div>
              );
            })}
          </div>
        </div>
      )}

      {explanation.mitreTechniques?.length > 0 && (
        <div className="cg-card space-y-3">
          <div className="flex items-center gap-2">
            <ShieldAlert size={18} className="text-cg-accent" />
            <h3 className="text-base font-semibold text-slate-100">MITRE ATT&amp;CK Mapping</h3>
          </div>
          {explanation.mitreTechniques.map((t) => (
            <div key={t.techniqueId} className="bg-cg-surface-alt border border-cg-border rounded-md p-3">
              <div className="flex items-center gap-2 mb-1">
                <span className="text-cg-accent font-mono text-sm">{t.techniqueId}</span>
                <span className="text-xs text-slate-500 bg-cg-bg px-2 py-0.5 rounded">{t.tactic}</span>
              </div>
              <p className="text-sm font-medium text-slate-200">{t.name}</p>
              <p className="text-xs text-slate-500 mt-1">{t.description}</p>
              <p className="text-xs text-slate-400 mt-2"><span className="text-slate-500">Mitigation:</span> {t.mitigation}</p>
            </div>
          ))}
        </div>
      )}
    </>
  );
}
