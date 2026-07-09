import { useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { ArrowLeft, Ban, PackageCheck, Search } from 'lucide-react';
import { SeverityBadge, StatusBadge } from '../components/common/Badge';
import ExplainAiPanel from '../components/common/ExplainAiPanel';
import { Skeleton } from '../components/common/Skeleton';
import { threatService } from '../services/threatService';
import { responseActionService } from '../services/responseActionService';
import { useAuth } from '../context/AuthContext';

export default function ThreatDetail() {
  const { id } = useParams();
  const navigate = useNavigate();
  const { hasRole } = useAuth();
  const [threat, setThreat] = useState(null);
  const [explanation, setExplanation] = useState(null);
  const [explanationError, setExplanationError] = useState('');
  const [busy, setBusy] = useState(false);

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
