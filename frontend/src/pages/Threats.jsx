import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { ShieldAlert, Inbox } from 'lucide-react';
import { SeverityBadge, StatusBadge } from '../components/common/Badge';
import { Skeleton } from '../components/common/Skeleton';
import EmptyState from '../components/common/EmptyState';
import { threatService } from '../services/threatService';

const SEVERITIES = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'];
const TYPES = ['MALWARE', 'DDOS', 'SQL_INJECTION', 'XSS', 'BRUTE_FORCE', 'PORT_SCAN', 'PHISHING', 'RANSOMWARE', 'INSIDER_THREAT'];

export default function Threats() {
  const [threats, setThreats] = useState([]);
  const [severity, setSeverity] = useState('');
  const [type, setType] = useState('');
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);

  const load = () => {
    setLoading(true);
    threatService
      .getThreats({ page, size: 15, severity: severity || undefined, type: type || undefined })
      .then(({ data }) => {
        setThreats(data.content);
        setTotalPages(data.totalPages);
      })
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [page, severity, type]);

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between flex-wrap gap-3">
        <div>
          <h2 className="text-lg font-semibold text-slate-100 flex items-center gap-2">
            <ShieldAlert size={20} className="text-cg-accent" /> AI Threat Detection Results
          </h2>
          <p className="text-sm text-slate-500 mt-1">Threats detected and classified by the AI engine in real time.</p>
        </div>
        <div className="flex gap-2">
          <select className="cg-input" value={severity} onChange={(e) => { setSeverity(e.target.value); setPage(0); }}>
            <option value="">All Severities</option>
            {SEVERITIES.map((s) => <option key={s} value={s}>{s}</option>)}
          </select>
          <select className="cg-input" value={type} onChange={(e) => { setType(e.target.value); setPage(0); }}>
            <option value="">All Types</option>
            {TYPES.map((t) => <option key={t} value={t}>{t.replace('_', ' ')}</option>)}
          </select>
        </div>
      </div>

      <div className="cg-card overflow-x-auto !p-0">
        <table className="w-full text-sm">
          <thead>
            <tr className="text-left text-slate-400 border-b border-cg-border">
              <th className="py-3 pl-5 pr-4">Type</th>
              <th className="py-3 pr-4">Severity</th>
              <th className="py-3 pr-4">Confidence</th>
              <th className="py-3 pr-4">Source</th>
              <th className="py-3 pr-4">Destination</th>
              <th className="py-3 pr-4">Status</th>
              <th className="py-3 pr-4">Detected</th>
            </tr>
          </thead>
          <tbody>
            {loading ? (
              Array.from({ length: 8 }).map((_, i) => (
                <tr key={i} className="border-b border-cg-border last:border-0">
                  <td className="py-2.5 pl-5 pr-4" colSpan={7}><Skeleton className="h-4 w-full" /></td>
                </tr>
              ))
            ) : threats.length === 0 ? (
              <tr><td colSpan={7}><EmptyState icon={Inbox} title="No threats found" message="Try adjusting your filters, or check back once new threats are detected." /></td></tr>
            ) : threats.map((t) => (
              <tr key={t.id} className="cg-table-row">
                <td className="py-2.5 pl-5 pr-4">
                  <Link to={`/threats/${t.id}`} className="text-slate-200 hover:text-cg-accent transition-colors">{t.threatType}</Link>
                </td>
                <td className="py-2.5 pr-4"><SeverityBadge severity={t.severity} /></td>
                <td className="py-2.5 pr-4 text-slate-400">{Number(t.confidenceScore).toFixed(1)}%</td>
                <td className="py-2.5 pr-4 text-slate-400 font-mono text-xs">{t.sourceIp}</td>
                <td className="py-2.5 pr-4 text-slate-400 font-mono text-xs">{t.destinationIp}</td>
                <td className="py-2.5 pr-4"><StatusBadge status={t.status} /></td>
                <td className="py-2.5 pr-4 text-slate-500 text-xs">{new Date(t.detectedAt).toLocaleString()}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {totalPages > 1 && (
        <div className="flex justify-center gap-2">
          <button disabled={page === 0} onClick={() => setPage((p) => p - 1)} className="cg-btn-secondary disabled:opacity-40">Prev</button>
          <span className="text-sm text-slate-400 self-center">Page {page + 1} of {totalPages}</span>
          <button disabled={page >= totalPages - 1} onClick={() => setPage((p) => p + 1)} className="cg-btn-secondary disabled:opacity-40">Next</button>
        </div>
      )}
    </div>
  );
}
