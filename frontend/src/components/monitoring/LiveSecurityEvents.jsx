import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import api from '../../services/api';
import { useEventStreamStatus } from '../../context/EventStreamContext';

const timestamp = (value) => value ? new Date(value).toISOString() : '—';
const merge = (oldRows, newRows) => [...new Map([...oldRows, ...newRows].map((row) => [row.id, row])).values()]
  .sort((a, b) => b.id - a.id).slice(0, 100);

export default function LiveSecurityEvents() {
  const { state } = useEventStreamStatus();
  const [rows, setRows] = useState([]);
  const [error, setError] = useState('');
  useEffect(() => {
    let active = true;
    const recover = () => api.get('/monitoring/security-events', { params: { size: 100 } })
      .then(({ data }) => { if (active) { setRows((old) => merge(data.content, old)); setError(''); } })
      .catch(() => { if (active) setError('Event history could not be loaded. It will be retried when the stream reconnects.'); });
    const receive = ({ detail }) => setRows((old) => merge(old, [detail]));
    window.addEventListener('cg:security-event', receive);
    window.addEventListener('cg:stream-connected', recover);
    recover();
    return () => {
      active = false;
      window.removeEventListener('cg:security-event', receive);
      window.removeEventListener('cg:stream-connected', recover);
    };
  }, []);
  return <section className="cg-card space-y-3">
    <div className="flex justify-between gap-3 flex-wrap">
      <h3 className="font-semibold text-slate-100">LIVE SECURITY EVENTS</h3>
      <span role="status" className={state === 'CONNECTED' ? 'text-cg-success' : 'text-cg-warning'}>{state}</span>
    </div>
    <p className="text-xs text-slate-400">Latest 100 real authentication events. Times are UTC. RULE evaluates the configured account lockout threshold; no ML predictions are made.</p>
    {error && <p role="alert" className="text-cg-warning text-sm">{error}</p>}
    <div className="overflow-x-auto">
      <table className="w-full text-sm text-left">
        <thead><tr className="text-slate-400 border-b border-cg-border">
          {['Time', 'Source', 'Event', 'Username', 'Source IP', 'Outcome', 'Detector', 'Result'].map((label) => <th key={label} className="p-2">{label}</th>)}
        </tr></thead>
        <tbody>{rows.map((row) => <tr key={row.id} className="border-b border-cg-border align-top">
          <td className="p-2 whitespace-nowrap text-xs"><time dateTime={row.observedAt}>{timestamp(row.observedAt)}</time>
            <details><summary className="cursor-pointer text-slate-500">Timing / evidence</summary>
              <p>Received: {timestamp(row.ingestedAt)}</p><p>Detected: {timestamp(row.detectedAt)}</p>
              <p>Processing: {row.processingLatencyMs ?? '—'} ms</p>
              <p>Event #{row.id} · Attempt #{row.loginAttemptId}</p>
              {row.evidence && <pre className="whitespace-pre-wrap max-w-sm">{row.evidence}</pre>}
            </details>
          </td>
          <td className="p-2">{row.source}</td><td className="p-2">{row.eventType}</td>
          <td className="p-2">{row.username}</td><td className="p-2 font-mono">{row.sourceIp || '—'}</td>
          <td className={`p-2 ${row.outcome === 'SUCCESS' ? 'text-cg-success' : 'text-cg-warning'}`}>{row.outcome}</td>
          <td className="p-2">{row.detectorType || '—'}</td>
          <td className="p-2 min-w-48">{row.result}
            {row.threatId && <Link className="block text-cg-accent" to={`/threats/${row.threatId}`}>RULE threat #{row.threatId}</Link>}
            {row.incidentId && <Link className="block text-cg-accent" to={`/incidents/${row.incidentId}`}>Incident #{row.incidentId}</Link>}
          </td>
        </tr>)}</tbody>
      </table>
      {!rows.length && <p className="py-5 text-slate-400">No authentication events recorded yet.</p>}
    </div>
  </section>;
}
