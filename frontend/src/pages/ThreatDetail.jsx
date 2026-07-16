import { useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { ArrowLeft, Ban, PackageCheck, Search, Sparkles } from 'lucide-react';
import { SeverityBadge, StatusBadge } from '../components/common/Badge';
import ExplainAiPanel from '../components/common/ExplainAiPanel';
import { Skeleton } from '../components/common/Skeleton';
import { threatService } from '../services/threatService';
import { responseActionService } from '../services/responseActionService';
import { useAuth } from '../context/AuthContext';

// Illustrative sample connection records for the attention-LSTM temporal
// detector demo below - NOT real captured traffic, just plausible
// characteristic values for each NSL-KDD category. Unspecified fields fall
// back to ai-service's KddConnectionRecord schema defaults.
const TEMPORAL_SAMPLE_SCENARIOS = {
  normal: {
    label: 'Normal traffic',
    record: {
      duration: 0, protocol_type: 'tcp', service: 'http', flag: 'SF',
      src_bytes: 200, dst_bytes: 300, count: 1, serror_rate: 0, same_srv_rate: 1.0,
    },
  },
  dos: {
    label: 'DoS (SYN flood-like)',
    record: {
      protocol_type: 'tcp', service: 'private', flag: 'S0', src_bytes: 0, dst_bytes: 0,
      count: 200, serror_rate: 1.0, srv_serror_rate: 1.0, same_srv_rate: 1.0,
      dst_host_count: 255, dst_host_serror_rate: 1.0, dst_host_srv_serror_rate: 1.0,
    },
  },
  probe: {
    label: 'Probe (port sweep-like)',
    record: {
      protocol_type: 'tcp', service: 'private', flag: 'REJ', src_bytes: 0, dst_bytes: 0,
      count: 30, diff_srv_rate: 0.5, dst_host_diff_srv_rate: 0.5, dst_host_srv_rerror_rate: 0.3,
    },
  },
};

export default function ThreatDetail() {
  const { id } = useParams();
  const navigate = useNavigate();
  const { hasRole } = useAuth();
  const [threat, setThreat] = useState(null);
  const [explanation, setExplanation] = useState(null);
  const [explanationError, setExplanationError] = useState('');
  const [busy, setBusy] = useState(false);
  const [temporalScenario, setTemporalScenario] = useState('normal');
  const [temporalResult, setTemporalResult] = useState(null);
  const [temporalError, setTemporalError] = useState('');
  const [temporalBusy, setTemporalBusy] = useState(false);

  const load = () => {
    threatService.getById(id).then(({ data }) => setThreat(data));
    threatService.explain(id)
      .then(({ data }) => setExplanation(data))
      .catch(() => setExplanationError('AI explanation is unavailable for this threat.'));
  };

  useEffect(() => { load(); }, [id]);

  const canRespond = hasRole('ROLE_ADMIN') || hasRole('ROLE_ANALYST');

  const handleBlockIp = async () => {
    setBusy(true);
    try {
      await responseActionService.blockIp(threat.sourceIp, `Manual block triggered from threat #${threat.id}`);
      await load();
    } finally {
      setBusy(false);
    }
  };

  const handleQuarantine = async () => {
    setBusy(true);
    try {
      await responseActionService.quarantine(threat.id);
      await load();
    } finally {
      setBusy(false);
    }
  };

  const handleRunTemporal = async () => {
    setTemporalBusy(true);
    setTemporalError('');
    setTemporalResult(null);
    try {
      const { data } = await threatService.predictTemporal([TEMPORAL_SAMPLE_SCENARIOS[temporalScenario].record]);
      setTemporalResult(data);
    } catch (err) {
      setTemporalError(
        err.response?.status === 503
          ? 'The attention-LSTM temporal detector (Model B) is currently unavailable.'
          : 'Could not reach the temporal detector demo endpoint.'
      );
    } finally {
      setTemporalBusy(false);
    }
  };

  if (!threat) {
    return (
      <div className="space-y-4 max-w-3xl">
        <Skeleton className="h-5 w-20" />
        <div className="cg-card space-y-4">
          <div className="flex items-center justify-between">
            <Skeleton className="h-6 w-64" />
            <Skeleton className="h-6 w-32" />
          </div>
          <div className="grid grid-cols-2 gap-4">
            {Array.from({ length: 5 }).map((_, i) => <Skeleton key={i} className="h-10 w-full" />)}
          </div>
        </div>
        <Skeleton className="h-48 w-full rounded-xl" />
      </div>
    );
  }

  return (
    <div className="space-y-4 max-w-3xl">
      <div className="flex items-center justify-between">
        <button onClick={() => navigate(-1)} className="flex items-center gap-1 text-sm text-slate-400 hover:text-cg-accent">
          <ArrowLeft size={16} /> Back
        </button>
        <Link to={`/threats/${id}/investigation`} className="cg-btn-secondary flex items-center gap-2 text-sm">
          <Search size={16} /> Investigate
        </Link>
      </div>

      <div className="cg-card space-y-4">
        <div className="flex items-center justify-between">
          <h2 className="text-lg font-semibold text-slate-100">{threat.threatType} Detection #{threat.id}</h2>
          <div className="flex gap-2">
            <SeverityBadge severity={threat.severity} />
            <StatusBadge status={threat.status} />
          </div>
        </div>

        <div className="grid grid-cols-2 gap-4 text-sm">
          <Info label="Confidence Score" value={`${Number(threat.confidenceScore).toFixed(2)}%`} />
          <Info label="Protocol" value={threat.protocol || 'N/A'} />
          <Info label="Source" value={`${threat.sourceIp || '-'}:${threat.sourcePort || '-'}`} />
          <Info label="Destination" value={`${threat.destinationIp || '-'}:${threat.destinationPort || '-'}`} />
          <Info label="Detected At" value={new Date(threat.detectedAt).toLocaleString()} />
          <Info label="Detection Model" value="RandomForest (Model A)" hint="The live detection pipeline always uses the RandomForest classifier today; the experimental attention-LSTM temporal detector (Model B) is available via a separate endpoint but isn't yet wired into this flow." />
        </div>

        <div>
          <p className="text-xs text-slate-400 uppercase mb-1">Recommended Action</p>
          <p className="text-sm text-slate-200 bg-cg-surface-alt border border-cg-border rounded-md p-3">
            {threat.recommendedAction || 'No specific recommendation available.'}
          </p>
        </div>

        {canRespond && (
          <div className="flex gap-3 pt-2 border-t border-cg-border">
            <button disabled={busy} onClick={handleBlockIp} className="cg-btn-secondary flex items-center gap-2 text-sm">
              <Ban size={16} /> Block Source IP
            </button>
            <button disabled={busy} onClick={handleQuarantine} className="cg-btn-secondary flex items-center gap-2 text-sm">
              <PackageCheck size={16} /> Quarantine Threat
            </button>
          </div>
        )}
      </div>

      <div className="cg-card space-y-3">
        <div className="flex items-center gap-2">
          <Sparkles size={16} className="text-cg-accent" />
          <h3 className="text-sm font-semibold text-slate-100">Temporal Detector Demo (Model B)</h3>
        </div>
        <p className="text-xs text-slate-400">
          Runs the attention-LSTM temporal detector (trained on NSL-KDD) against an illustrative sample
          record - not a real captured packet - so you can see it work independently of the primary
          RandomForest detector above.
        </p>
        <div className="flex flex-wrap items-center gap-2">
          <select
            value={temporalScenario}
            onChange={(e) => setTemporalScenario(e.target.value)}
            className="cg-input text-sm w-auto"
          >
            {Object.entries(TEMPORAL_SAMPLE_SCENARIOS).map(([key, { label }]) => (
              <option key={key} value={key}>{label}</option>
            ))}
          </select>
          <button disabled={temporalBusy} onClick={handleRunTemporal} className="cg-btn-secondary text-sm">
            {temporalBusy ? 'Running…' : 'Run Temporal Detection'}
          </button>
        </div>

        {temporalError && <p className="text-sm text-amber-400">{temporalError}</p>}

        {temporalResult && (
          <div className="bg-cg-surface-alt border border-cg-border rounded-md p-3 space-y-2 text-sm">
            <div className="flex items-center justify-between">
              <span className="text-slate-200 font-mono">{temporalResult.threatCategory}</span>
              <span className="text-slate-400">{(temporalResult.confidenceScore * 100).toFixed(1)}% confidence</span>
            </div>
            <div className="grid grid-cols-2 gap-x-4 gap-y-1 text-xs text-slate-400">
              {Object.entries(temporalResult.classProbabilities || {}).map(([cls, prob]) => (
                <div key={cls} className="flex justify-between">
                  <span>{cls}</span>
                  <span className="font-mono">{(prob * 100).toFixed(1)}%</span>
                </div>
              ))}
            </div>
            <p className="text-xs text-slate-500 pt-1 border-t border-cg-border">{temporalResult.note}</p>
          </div>
        )}
      </div>

      {explanationError && <p className="text-sm text-slate-500">{explanationError}</p>}
      <ExplainAiPanel explanation={explanation} />
    </div>
  );
}

function Info({ label, value, hint }) {
  return (
    <div title={hint}>
      <p className="text-xs text-slate-500 uppercase">{label}</p>
      <p className="text-slate-200 font-mono text-sm">{value}</p>
    </div>
  );
}
