import { useEffect, useState } from 'react';
import { Activity, Inbox } from 'lucide-react';
import { monitoringService } from '../services/monitoringService';
import { Skeleton } from '../components/common/Skeleton';
import EmptyState from '../components/common/EmptyState';

const TABS = ['System Logs', 'Login Attempts', 'Network Events'];

function TableSkeleton({ columns }) {
  return (
    <>
      {Array.from({ length: 6 }).map((_, i) => (
        <tr key={i} className="border-b border-cg-border last:border-0">
          <td className="py-2.5 pr-4" colSpan={columns}><Skeleton className="h-4 w-full" /></td>
        </tr>
      ))}
    </>
  );
}

export default function Monitoring() {
  const [tab, setTab] = useState(0);
  const [systemLogs, setSystemLogs] = useState([]);
  const [loginAttempts, setLoginAttempts] = useState([]);
  const [networkEvents, setNetworkEvents] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    setLoading(true);
    if (tab === 0) monitoringService.getSystemLogs().then(({ data }) => setSystemLogs(data.content)).finally(() => setLoading(false));
    if (tab === 1) monitoringService.getLoginAttempts().then(({ data }) => setLoginAttempts(data.content)).finally(() => setLoading(false));
    if (tab === 2) monitoringService.getNetworkEvents().then(({ data }) => setNetworkEvents(data.content)).finally(() => setLoading(false));
  }, [tab]);

  const levelColor = { INFO: 'text-cg-info', WARN: 'text-cg-warning', ERROR: 'text-cg-danger', CRITICAL: 'text-cg-danger font-bold' };

  return (
    <div className="space-y-4">
      <div>
        <h2 className="text-lg font-semibold text-slate-100 flex items-center gap-2">
          <Activity size={20} className="text-cg-accent" /> Real-Time Security Monitoring
        </h2>
        <p className="text-sm text-slate-500 mt-1">Live system logs, authentication attempts, and network events.</p>
      </div>

      <div className="flex gap-2 border-b border-cg-border">
        {TABS.map((t, idx) => (
          <button
            key={t}
            onClick={() => setTab(idx)}
            className={`px-4 py-2 text-sm font-medium border-b-2 transition-colors duration-150 ${
              tab === idx ? 'border-cg-accent text-cg-accent' : 'border-transparent text-slate-400 hover:text-slate-200'
            }`}
          >
            {t}
          </button>
        ))}
      </div>

      {tab === 0 && (
        <div className="cg-card overflow-x-auto !p-0">
          <table className="w-full text-sm">
            <thead><tr className="text-left text-slate-400 border-b border-cg-border">
              <th className="py-3 pl-5 pr-4">Level</th><th className="py-3 pr-4">Source</th><th className="py-3 pr-4">Message</th><th className="py-3 pr-4">IP</th><th className="py-3 pr-4">Time</th>
            </tr></thead>
            <tbody>
              {loading ? <TableSkeleton columns={5} /> : systemLogs.length === 0 ? (
                <tr><td colSpan={5}><EmptyState icon={Inbox} title="No system logs" message="System log entries will appear here as they occur." /></td></tr>
              ) : systemLogs.map((l) => (
                <tr key={l.id} className="cg-table-row">
                  <td className={`py-2.5 pl-5 pr-4 font-semibold ${levelColor[l.logLevel]}`}>{l.logLevel}</td>
                  <td className="py-2.5 pr-4 text-slate-300">{l.source}</td>
                  <td className="py-2.5 pr-4 text-slate-400">{l.message}</td>
                  <td className="py-2.5 pr-4 font-mono text-xs text-slate-500">{l.ipAddress}</td>
                  <td className="py-2.5 pr-4 text-xs text-slate-500">{new Date(l.createdAt).toLocaleString()}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {tab === 1 && (
        <div className="cg-card overflow-x-auto !p-0">
          <table className="w-full text-sm">
            <thead><tr className="text-left text-slate-400 border-b border-cg-border">
              <th className="py-3 pl-5 pr-4">Username</th><th className="py-3 pr-4">IP</th><th className="py-3 pr-4">Result</th><th className="py-3 pr-4">Time</th>
            </tr></thead>
            <tbody>
              {loading ? <TableSkeleton columns={4} /> : loginAttempts.length === 0 ? (
                <tr><td colSpan={4}><EmptyState icon={Inbox} title="No login attempts" message="Authentication attempts will appear here as they occur." /></td></tr>
              ) : loginAttempts.map((a) => (
                <tr key={a.id} className="cg-table-row">
                  <td className="py-2.5 pl-5 pr-4 text-slate-300">{a.usernameAttempted}</td>
                  <td className="py-2.5 pr-4 font-mono text-xs text-slate-500">{a.ipAddress}</td>
                  <td className={`py-2.5 pr-4 font-semibold ${a.success ? 'text-cg-success' : 'text-cg-danger'}`}>{a.success ? 'Success' : 'Failed'}</td>
                  <td className="py-2.5 pr-4 text-xs text-slate-500">{new Date(a.createdAt).toLocaleString()}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {tab === 2 && (
        <div className="cg-card overflow-x-auto !p-0">
          <table className="w-full text-sm">
            <thead><tr className="text-left text-slate-400 border-b border-cg-border">
              <th className="py-3 pl-5 pr-4">Source</th><th className="py-3 pr-4">Destination</th><th className="py-3 pr-4">Protocol</th><th className="py-3 pr-4">Bytes</th><th className="py-3 pr-4">Flagged</th>
            </tr></thead>
            <tbody>
              {loading ? <TableSkeleton columns={5} /> : networkEvents.length === 0 ? (
                <tr><td colSpan={5}><EmptyState icon={Inbox} title="No network events" message="Captured network traffic events will appear here." /></td></tr>
              ) : networkEvents.map((e) => (
                <tr key={e.id} className="cg-table-row">
                  <td className="py-2.5 pl-5 pr-4 font-mono text-xs text-slate-400">{e.sourceIp}:{e.sourcePort}</td>
                  <td className="py-2.5 pr-4 font-mono text-xs text-slate-400">{e.destinationIp}:{e.destinationPort}</td>
                  <td className="py-2.5 pr-4 text-slate-400">{e.protocol}</td>
                  <td className="py-2.5 pr-4 text-slate-400">{e.bytesTransferred}</td>
                  <td className="py-2.5 pr-4">{e.flagged ? <span className="text-cg-danger font-semibold">Yes</span> : <span className="text-slate-500">No</span>}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
