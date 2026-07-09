import { useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { ArrowLeft, Bot, Ban, PackageCheck, Bell, FileWarning, ShieldCheck, MessageSquare, Circle, AlertTriangle } from 'lucide-react';
import { SeverityBadge, StatusBadge, ResponseModeBadge, detectResponseMode } from '../components/common/Badge';
import { Skeleton } from '../components/common/Skeleton';
import EmptyState from '../components/common/EmptyState';
import { threatService } from '../services/threatService';

const EVENT_ICONS = {
  DETECTED: Bot,
  RESPONSE_ACTION_BLOCK_IP: Ban,
  RESPONSE_ACTION_QUARANTINE_THREAT: PackageCheck,
  RESPONSE_ACTION_NOTIFY_ADMIN: Bell,
  RESPONSE_ACTION_GENERATE_INCIDENT: FileWarning,
  INCIDENT_ASSIGNED: ShieldCheck,
  INCIDENT_COMMENTED: MessageSquare,
  INCIDENT_STATUS_CHANGED: Circle,
};

export default function Investigation() {
  const { id } = useParams();
  const navigate = useNavigate();
  const [data, setData] = useState(null);
  const [error, setError] = useState('');

  useEffect(() => {
    threatService.investigate(id)
      .then(({ data }) => setData(data))
      .catch(() => setError('Failed to load investigation timeline for this threat.'));
  }, [id]);

  if (error) return <EmptyState icon={AlertTriangle} title="Investigation unavailable" message={error} />;
  if (!data) {
    return (
      <div className="space-y-4 max-w-3xl">
        <Skeleton className="h-5 w-20" />
        <div className="cg-card space-y-3">
          <Skeleton className="h-6 w-72" />
          <div className="grid grid-cols-3 gap-4">
            {Array.from({ length: 3 }).map((_, i) => <Skeleton key={i} className="h-10 w-full" />)}
          </div>
        </div>
        <div className="cg-card space-y-4">
          {Array.from({ length: 3 }).map((_, i) => <Skeleton key={i} className="h-14 w-full" />)}
        </div>
      </div>
    );
  }

  const { threat, explanation, incident, timeline } = data;

  return (
    <div className="space-y-4 max-w-3xl">
      <button onClick={() => navigate(-1)} className="flex items-center gap-1 text-sm text-slate-400 hover:text-cg-accent">
        <ArrowLeft size={16} /> Back
      </button>

      <div className="cg-card space-y-3">
        <div className="flex items-center justify-between flex-wrap gap-2">
          <div>
            <p className="text-xs text-slate-500">Attack Investigation</p>
            <h2 className="text-lg font-semibold text-slate-100">{threat.threatType} from {threat.sourceIp}</h2>
          </div>
          <div className="flex gap-2">
            <SeverityBadge severity={threat.severity} />
            <StatusBadge status={threat.status} />
          </div>
        </div>
        <div className="grid grid-cols-3 gap-4 text-sm">
          <div>
            <p className="text-xs text-slate-500 uppercase">Risk Score</p>
            <p className="text-slate-200 font-semibold">{Number(explanation.riskScore).toFixed(0)}/100</p>
          </div>
          <div>
            <p className="text-xs text-slate-500 uppercase">Confidence</p>
            <p className="text-slate-200 font-semibold">{Number(threat.confidenceScore).toFixed(0)}%</p>
          </div>
          <div>
            <p className="text-xs text-slate-500 uppercase">Incident</p>
            {incident ? (
              <Link to={`/incidents/${incident.id}`} className="text-cg-accent font-semibold hover:underline">
                {incident.incidentNumber}
              </Link>
            ) : (
              <p className="text-slate-500">None created</p>
            )}
          </div>
        </div>
      </div>

      <div className="cg-card">
        <h3 className="text-sm font-semibold text-slate-300 mb-4">Chronological Timeline: Detection &rarr; Mitigation</h3>
        <div className="space-y-0">
          {timeline.map((event, idx) => {
            const Icon = EVENT_ICONS[event.eventType] || Circle;
            const isLast = idx === timeline.length - 1;
            return (
              <div key={idx} className="flex gap-3">
                <div className="flex flex-col items-center">
                  <div className="w-8 h-8 rounded-full bg-cg-accent/15 text-cg-accent flex items-center justify-center shrink-0">
                    <Icon size={15} />
                  </div>
                  {!isLast && <div className="w-px flex-1 bg-cg-border my-1" />}
                </div>
                <div className="pb-6">
                  <div className="flex items-center gap-2 flex-wrap">
                    <p className="text-sm font-medium text-slate-200">{event.title}</p>
                    <ResponseModeBadge mode={detectResponseMode(event.description)} />
                    <span className="text-xs text-slate-600 font-mono">{new Date(event.timestamp).toLocaleString()}</span>
                  </div>
                  <p className="text-sm text-slate-400 mt-0.5">{event.description}</p>
                  <p className="text-xs text-slate-600 mt-0.5">by {event.actor}</p>
                </div>
              </div>
            );
          })}
        </div>
      </div>

      {incident && (
        <div className="cg-card">
          <p className="text-sm text-slate-400">
            An incident was created from this threat.{' '}
            <Link to={`/incidents/${incident.id}`} className="text-cg-accent hover:underline">
              View full incident details, comments and assignment &rarr;
            </Link>
          </p>
        </div>
      )}
    </div>
  );
}
