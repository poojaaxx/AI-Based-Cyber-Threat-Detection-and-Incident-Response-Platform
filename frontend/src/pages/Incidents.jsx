import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { Plus, FileWarning, Inbox } from 'lucide-react';
import { SeverityBadge, StatusBadge } from '../components/common/Badge';
import { Skeleton } from '../components/common/Skeleton';
import EmptyState from '../components/common/EmptyState';
import { incidentService } from '../services/incidentService';

const STATUSES = ['OPEN', 'ASSIGNED', 'IN_PROGRESS', 'RESOLVED', 'CLOSED'];
const SEVERITIES = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'];

export default function Incidents() {
  const [incidents, setIncidents] = useState([]);
  const [status, setStatus] = useState('');
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [loading, setLoading] = useState(true);
  const [showModal, setShowModal] = useState(false);
  const [form, setForm] = useState({ title: '', description: '', severity: 'MEDIUM' });
  const [creating, setCreating] = useState(false);

  const load = () => {
    setLoading(true);
    incidentService
      .getIncidents({ page, size: 15, status: status || undefined })
      .then(({ data }) => {
        setIncidents(data.content);
        setTotalPages(data.totalPages);
      })
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [page, status]);

  const handleCreate = async (e) => {
    e.preventDefault();
    setCreating(true);
    try {
      await incidentService.create(form);
      setShowModal(false);
      setForm({ title: '', description: '', severity: 'MEDIUM' });
      load();
    } finally {
      setCreating(false);
    }
  };

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between flex-wrap gap-3">
        <div>
          <h2 className="text-lg font-semibold text-slate-100 flex items-center gap-2">
            <FileWarning size={20} className="text-cg-accent" /> Incident Management
          </h2>
          <p className="text-sm text-slate-500 mt-1">Track, assign, and resolve security incidents.</p>
        </div>
        <div className="flex gap-2">
          <select className="cg-input" value={status} onChange={(e) => { setStatus(e.target.value); setPage(0); }}>
            <option value="">All Statuses</option>
            {STATUSES.map((s) => <option key={s} value={s}>{s.replace('_', ' ')}</option>)}
          </select>
          <button onClick={() => setShowModal(true)} className="cg-btn-primary flex items-center gap-1 text-sm">
            <Plus size={16} /> New Incident
          </button>
        </div>
      </div>

      <div className="cg-card overflow-x-auto !p-0">
        <table className="w-full text-sm">
          <thead>
            <tr className="text-left text-slate-400 border-b border-cg-border">
              <th className="py-3 pl-5 pr-4">Incident #</th>
              <th className="py-3 pr-4">Title</th>
              <th className="py-3 pr-4">Severity</th>
              <th className="py-3 pr-4">Status</th>
              <th className="py-3 pr-4">Assigned To</th>
              <th className="py-3 pr-4">Created</th>
            </tr>
          </thead>
          <tbody>
            {loading ? (
              Array.from({ length: 8 }).map((_, i) => (
                <tr key={i} className="border-b border-cg-border last:border-0">
                  <td className="py-2.5 pl-5 pr-4" colSpan={6}><Skeleton className="h-4 w-full" /></td>
                </tr>
              ))
            ) : incidents.length === 0 ? (
              <tr><td colSpan={6}><EmptyState icon={Inbox} title="No incidents found" message="Try adjusting your filters, or create a new incident." actionLabel="New Incident" onAction={() => setShowModal(true)} /></td></tr>
            ) : incidents.map((i) => (
              <tr key={i.id} className="cg-table-row">
                <td className="py-2.5 pl-5 pr-4">
                  <Link to={`/incidents/${i.id}`} className="text-slate-200 hover:text-cg-accent font-mono text-xs transition-colors">{i.incidentNumber}</Link>
                </td>
                <td className="py-2.5 pr-4 text-slate-300">{i.title}</td>
                <td className="py-2.5 pr-4"><SeverityBadge severity={i.severity} /></td>
                <td className="py-2.5 pr-4"><StatusBadge status={i.status} /></td>
                <td className="py-2.5 pr-4 text-slate-400">{i.assignedTo?.username || 'Unassigned'}</td>
                <td className="py-2.5 pr-4 text-slate-500 text-xs">{new Date(i.createdAt).toLocaleString()}</td>
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

      {showModal && (
        <div className="fixed inset-0 bg-black/60 flex items-center justify-center z-50 px-4">
          <form onSubmit={handleCreate} className="cg-card w-full max-w-md space-y-3">
            <h3 className="text-lg font-semibold text-slate-100">Create Incident</h3>
            <div>
              <label className="block text-xs text-slate-400 mb-1">Title</label>
              <input required maxLength={255} className="cg-input w-full" value={form.title} onChange={(e) => setForm({ ...form, title: e.target.value })} />
            </div>
            <div>
              <label className="block text-xs text-slate-400 mb-1">Description</label>
              <textarea maxLength={5000} className="cg-input w-full" rows={3} value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} />
            </div>
            <div>
              <label className="block text-xs text-slate-400 mb-1">Severity</label>
              <select className="cg-input w-full" value={form.severity} onChange={(e) => setForm({ ...form, severity: e.target.value })}>
                {SEVERITIES.map((s) => <option key={s} value={s}>{s}</option>)}
              </select>
            </div>
            <div className="flex gap-2 justify-end pt-2">
              <button type="button" onClick={() => setShowModal(false)} className="cg-btn-secondary">Cancel</button>
              <button type="submit" disabled={creating} className="cg-btn-primary">{creating ? 'Creating...' : 'Create'}</button>
            </div>
          </form>
        </div>
      )}
    </div>
  );
}
