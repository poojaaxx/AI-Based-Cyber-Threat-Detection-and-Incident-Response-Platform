import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import api from '../../services/api';
import { useEventStreamStatus } from '../../context/EventStreamContext';

const timestamp = value => value ? new Date(value).toISOString() : '-';
const merge = (oldRows, newRows) => [...new Map([...oldRows, ...newRows].map(row => [row.id, row])).values()]
  .sort((a, b) => b.id - a.id).slice(0, 100);
const sources = { All: undefined, Authentication: 'CYBERGUARD_AUTH', Network: 'NETWORK' };

export default function LiveSecurityEvents() {
  const { state } = useEventStreamStatus();
  const [filter, setFilter] = useState('All');
  const [rows, setRows] = useState([]);
  const [error, setError] = useState('');
  const [health, setHealth] = useState(null);
  const [healthError, setHealthError] = useState('');
  useEffect(() => {
    let active = true;
    const load = () => api.get('/monitoring/collector-status').then(({ data }) => {
      if (active) { setHealth(data); setHealthError(''); }
    }).catch(() => { if (active) setHealthError('Collector status unavailable'); });
    const receive = ({ detail }) => { setHealth(detail); setHealthError(''); };
    window.addEventListener('cg:collector-status', receive);
    window.addEventListener('cg:stream-connected', load);
    load();
    return () => { active = false; window.removeEventListener('cg:collector-status', receive); window.removeEventListener('cg:stream-connected', load); };
  }, []);
  useEffect(() => {
    let active = true;
    setRows([]);
    const source = sources[filter];
    const recover = () => api.get('/monitoring/security-events', { params: { size: 100, source } })
      .then(({ data }) => { if (active) { setRows(old => merge(data.content, old)); setError(''); } })
      .catch(() => { if (active) setError('Event history could not be loaded. It will be retried when the stream reconnects.'); });
    const receive = ({ detail }) => {
      if (active && (!source || detail.source === source)) setRows(old => merge(old, [detail]));
    };
    window.addEventListener('cg:security-event', receive);
    window.addEventListener('cg:stream-connected', recover);
    recover();
    return () => { active = false; window.removeEventListener('cg:security-event', receive); window.removeEventListener('cg:stream-connected', recover); };
  }, [filter]);
  const authOnly = filter === 'Authentication';
  const networkOnly = filter === 'Network';
  const headings = ['Time', 'Source', 'Event', ...(!networkOnly ? ['Username', 'Outcome'] : []),
    ...(authOnly ? ['Source IP'] : [networkOnly ? 'Local IP' : 'Local IP / Auth source IP', 'Local Port', 'Remote IP', 'Remote Port', 'Protocol', 'Process']), 'Detector', 'Result'];
  return <section className="cg-card space-y-3">
    <h3 className="font-semibold text-slate-100">LIVE SECURITY EVENTS</h3>
    <div className="flex gap-4 flex-wrap text-sm" role="status">
      <span className={state === 'CONNECTED' ? 'text-cg-success' : 'text-cg-warning'}>Dashboard stream: {state}</span>
      <span className={health?.status === 'AVAILABLE' && state === 'CONNECTED' && !healthError ? 'text-cg-success' : 'text-slate-400'}>
        Windows TCP observer: {healthError || health?.status || 'Loading'}
        {health && state !== 'CONNECTED' && ' (last reported; live status unverified)'}
      </span>
    </div>
    {health && <details className="text-xs text-slate-400"><summary className="cursor-pointer">Collector health and coverage</summary>
      <p>Collector: {health.collectorId} / Session: {health.sessionId || '-'}</p>
      <p>Last heartbeat: {timestamp(health.lastHeartbeatAt)} / Last successful sample: {timestamp(health.lastSuccessfulSampleAt)}</p>
      <p>Queued: {health.queuedEvents} / Dropped this session: {health.droppedEvents} / Sampling gaps: {health.gapCount} / Accepted: {health.receivedEvents}</p>
      <p>Sampling error: {health.sampleError || 'None reported'} / Delivery error: {health.deliveryError || 'None reported'}. Restarts establish a new baseline; downtime is not observed.</p>
    </details>}
    <div className="flex gap-2" aria-label="Event source filters">
      {Object.keys(sources).map(name => <button key={name} type="button" onClick={() => setFilter(name)} aria-pressed={filter === name}
        className={`px-3 py-1 rounded border text-sm ${filter === name ? 'border-cg-accent text-cg-accent' : 'border-cg-border text-slate-400'}`}>{name}</button>)}
    </div>
    <p className="text-xs text-slate-400">Sampled TCP connection activity: local and remote endpoints have no inferred direction. Short connections may be missed. EVENT means observation, not an attack or an ML prediction. Latest 100 matching events; times are UTC.</p>
    {error && <p role="alert" className="text-cg-warning text-sm">{error}</p>}
    <div className="overflow-x-auto"><table className="w-full text-sm text-left">
      <thead><tr className="text-slate-400 border-b border-cg-border">{headings.map(label => <th key={label} className="p-2 whitespace-nowrap">{label}</th>)}</tr></thead>
      <tbody>{rows.map(row => {
        const network = row.networkEvent;
        return <tr key={row.id} className="border-b border-cg-border align-top text-slate-300">
          <td className="p-2 whitespace-nowrap text-xs"><time dateTime={row.observedAt}>{timestamp(row.observedAt)}</time>
            <details><summary className="cursor-pointer text-slate-500">Timing / evidence</summary>
              <p>Received: {timestamp(row.ingestedAt)}</p><p>Detected: {timestamp(row.detectedAt)}</p>
              {row.processingLatencyMs != null && <p>Processing: {row.processingLatencyMs} ms</p>}
              <p>Event #{row.id}{row.loginAttemptId != null && ` / Attempt #${row.loginAttemptId}`}</p>
              {network && <><p>TCP state: {network.tcpState}</p><p>Connection: {network.connectionId}</p>
                <p>Reported creation: {timestamp(network.connectionCreatedAt)}</p><p>Session: {network.sessionId} / Sequence: {network.sequence}</p></>}
              {row.evidence && <pre className="whitespace-pre-wrap max-w-sm">{row.evidence}</pre>}
            </details>
          </td>
          <td className="p-2">{row.source}</td><td className="p-2">{row.eventType}</td>
          {!networkOnly && <><td className="p-2">{row.username || '-'}</td><td className="p-2">{row.outcome}</td></>}
          <td className="p-2 font-mono">{network?.localIp || row.sourceIp || '-'}</td>
          {!authOnly && <><td className="p-2">{network?.localPort ?? '-'}</td><td className="p-2 font-mono">{network?.remoteIp || '-'}</td>
            <td className="p-2">{network?.remotePort ?? '-'}</td><td className="p-2">{network?.protocol || '-'}</td>
            <td className="p-2">{network ? `${network.processName || 'Unknown'} (PID ${network.processId ?? '-'})` : '-'}</td></>}
          <td className="p-2">{row.detectorType || '-'}</td><td className="p-2 min-w-48">{row.result}
            {network?.ruleId && <p className="text-xs">{network.ruleId}</p>}
            {row.threatId && <Link className="block text-cg-accent" to={`/threats/${row.threatId}`}>RULE threat #{row.threatId}</Link>}
            {row.incidentId && <Link className="block text-cg-accent" to={`/incidents/${row.incidentId}`}>Incident #{row.incidentId}</Link>}
          </td>
        </tr>;
      })}</tbody>
    </table>{!rows.length && <p className="py-5 text-slate-400">No matching events recorded yet.</p>}</div>
  </section>;
}
