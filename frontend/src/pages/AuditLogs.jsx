import { useEffect, useState } from 'react';
import { ScrollText, Inbox } from 'lucide-react';
import { auditService } from '../services/auditService';
import { Skeleton } from '../components/common/Skeleton';
import EmptyState from '../components/common/EmptyState';

export default function AuditLogs() {
  const [logs, setLogs] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    auditService.getAuditLogs({ size: 40 }).then(({ data }) => setLogs(data.content)).finally(() => setLoading(false));
  }, []);

  return (
    <div className="space-y-4">
      <div>
        <h2 className="text-lg font-semibold text-slate-100 flex items-center gap-2">
          <ScrollText size={20} className="text-cg-accent" /> Audit Logs
        </h2>
        <p className="text-sm text-slate-500 mt-1">A record of administrative and security-relevant actions across the platform.</p>
      </div>

      <div className="cg-card overflow-x-auto !p-0">
        <table className="w-full text-sm">
          <thead>
            <tr className="text-left text-slate-400 border-b border-cg-border">
              <th className="py-3 pl-5 pr-4">Action</th>
              <th className="py-3 pr-4">Entity</th>
              <th className="py-3 pr-4">Details</th>
              <th className="py-3 pr-4">User</th>
              <th className="py-3 pr-4">IP</th>
              <th className="py-3 pr-4">Time</th>
            </tr>
          </thead>
          <tbody>
            {loading ? (
              Array.from({ length: 8 }).map((_, i) => (
                <tr key={i} className="border-b border-cg-border last:border-0">
                  <td className="py-2.5 pl-5 pr-4" colSpan={6}><Skeleton className="h-4 w-full" /></td>
                </tr>
              ))
            ) : logs.length === 0 ? (
              <tr><td colSpan={6}><EmptyState icon={Inbox} title="No audit entries" message="Administrative and security actions will be recorded here." /></td></tr>
            ) : logs.map((l) => (
              <tr key={l.id} className="cg-table-row">
                <td className="py-2.5 pl-5 pr-4 text-cg-accent text-xs font-mono">{l.action}</td>
                <td className="py-2.5 pr-4 text-slate-400">{l.entityType} {l.entityId ? `#${l.entityId}` : ''}</td>
                <td className="py-2.5 pr-4 text-slate-400 max-w-xs truncate">{l.details}</td>
                <td className="py-2.5 pr-4 text-slate-400">{l.user?.username || 'system'}</td>
                <td className="py-2.5 pr-4 font-mono text-xs text-slate-500">{l.ipAddress}</td>
                <td className="py-2.5 pr-4 text-xs text-slate-500">{new Date(l.createdAt).toLocaleString()}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
