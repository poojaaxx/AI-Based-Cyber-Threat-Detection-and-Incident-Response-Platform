import { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { ArrowLeft, Send } from 'lucide-react';
import { SeverityBadge, StatusBadge } from '../components/common/Badge';
import { Skeleton } from '../components/common/Skeleton';
import { incidentService } from '../services/incidentService';

const STATUSES = ['OPEN', 'ASSIGNED', 'IN_PROGRESS', 'RESOLVED', 'CLOSED'];

export default function IncidentDetail() {
  const { id } = useParams();
  const navigate = useNavigate();
  const [incident, setIncident] = useState(null);
  const [timeline, setTimeline] = useState([]);
  const [comments, setComments] = useState([]);
  const [comment, setComment] = useState('');
  const [posting, setPosting] = useState(false);
  const [assignableUsers, setAssignableUsers] = useState([]);
  const [assigning, setAssigning] = useState(false);

  const loadAll = () => {
    incidentService.getById(id).then(({ data }) => setIncident(data));
    incidentService.getTimeline(id).then(({ data }) => setTimeline(data));
    incidentService.getComments(id).then(({ data }) => setComments(data));
  };

  useEffect(() => { loadAll(); }, [id]);
  useEffect(() => {
    incidentService.getAssignableUsers().then(({ data }) => setAssignableUsers(data)).catch(() => {});
  }, []);

  const handleStatusChange = async (status) => {
    await incidentService.update(id, { status });
    loadAll();
  };

  const handleAssigneeChange = async (assignedTo) => {
    // The backend only applies assignedTo when a value is present (see
    // UpdateIncidentRequest/IncidentService.updateIncident) - there is currently no
    // "unassign" operation, so the placeholder option is not itself submittable.
    if (!assignedTo) return;
    setAssigning(true);
    try {
      await incidentService.update(id, { assignedTo: Number(assignedTo) });
      loadAll();
    } finally {
      setAssigning(false);
    }
  };

  const handleComment = async (e) => {
    e.preventDefault();
    if (!comment.trim()) return;
    setPosting(true);
    try {
      await incidentService.addComment(id, comment);
      setComment('');
      loadAll();
    } finally {
      setPosting(false);
    }
  };

  if (!incident) {
    return (
      <div className="space-y-4 max-w-3xl">
        <Skeleton className="h-5 w-20" />
        <div className="cg-card space-y-4">
          <div className="flex items-center justify-between">
            <Skeleton className="h-6 w-56" />
            <Skeleton className="h-6 w-28" />
          </div>
          <div className="flex gap-4">
            <Skeleton className="h-10 w-40" />
            <Skeleton className="h-10 w-40" />
          </div>
        </div>
        <Skeleton className="h-32 w-full rounded-xl" />
        <Skeleton className="h-32 w-full rounded-xl" />
      </div>
    );
  }

  return (
    <div className="space-y-4 max-w-3xl">
      <button onClick={() => navigate(-1)} className="flex items-center gap-1 text-sm text-slate-400 hover:text-cg-accent">
        <ArrowLeft size={16} /> Back
      </button>

      <div className="cg-card space-y-4">
        <div className="flex items-center justify-between flex-wrap gap-2">
          <div>
            <p className="text-xs text-slate-500 font-mono">{incident.incidentNumber}</p>
            <h2 className="text-lg font-semibold text-slate-100">{incident.title}</h2>
          </div>
          <div className="flex gap-2">
            <SeverityBadge severity={incident.severity} />
            <StatusBadge status={incident.status} />
          </div>
        </div>

        <p className="text-sm text-slate-400">{incident.description || 'No description provided.'}</p>

        <div className="flex flex-wrap gap-4">
          <div>
            <label className="block text-xs text-slate-400 mb-1">Update Status</label>
            <select className="cg-input" value={incident.status} onChange={(e) => handleStatusChange(e.target.value)}>
              {STATUSES.map((s) => <option key={s} value={s}>{s.replace('_', ' ')}</option>)}
            </select>
          </div>
          <div>
            <label className="block text-xs text-slate-400 mb-1">Assign Analyst</label>
            <select
              className="cg-input"
              disabled={assigning}
              value={incident.assignedTo?.id || ''}
              onChange={(e) => handleAssigneeChange(e.target.value)}
            >
              <option value="" disabled>{incident.assignedTo ? incident.assignedTo.username : 'Unassigned'}</option>
              {assignableUsers.map((u) => (
                <option key={u.id} value={u.id}>{u.fullName} ({u.username})</option>
              ))}
            </select>
          </div>
        </div>
      </div>

      <div className="cg-card">
        <h3 className="text-sm font-semibold text-slate-300 mb-3">Timeline</h3>
        <div className="space-y-3">
          {timeline.map((t) => (
            <div key={t.id} className="flex gap-3 text-sm">
              <span className="text-cg-accent font-mono text-xs mt-0.5">{new Date(t.createdAt).toLocaleTimeString()}</span>
              <div>
                <p className="text-slate-200">{t.eventType.replace('_', ' ')}</p>
                <p className="text-slate-500 text-xs">{t.description}</p>
              </div>
            </div>
          ))}
        </div>
      </div>

      <div className="cg-card">
        <h3 className="text-sm font-semibold text-slate-300 mb-3">Comments</h3>
        <div className="space-y-3 mb-4 max-h-64 overflow-y-auto">
          {comments.map((c) => (
            <div key={c.id} className="border-b border-cg-border pb-2 last:border-0">
              <p className="text-sm text-slate-200">{c.comment}</p>
              <p className="text-xs text-slate-500">{c.user?.username} &middot; {new Date(c.createdAt).toLocaleString()}</p>
            </div>
          ))}
          {comments.length === 0 && <p className="text-sm text-slate-500">No comments yet.</p>}
        </div>
        <form onSubmit={handleComment} className="flex gap-2">
          <input
            className="cg-input flex-1"
            placeholder="Add a comment..."
            maxLength={2000}
            value={comment}
            onChange={(e) => setComment(e.target.value)}
          />
          <button type="submit" disabled={posting} aria-label="Post comment" className="cg-btn-primary flex items-center gap-1">
            <Send size={16} />
          </button>
        </form>
      </div>
    </div>
  );
}
