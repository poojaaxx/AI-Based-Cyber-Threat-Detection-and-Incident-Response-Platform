import { useEffect, useState } from 'react';
import { CheckCheck, Bell, Inbox } from 'lucide-react';
import { notificationService } from '../services/notificationService';
import { Skeleton } from '../components/common/Skeleton';
import EmptyState from '../components/common/EmptyState';

const TYPE_COLORS = {
  CRITICAL: 'border-cg-danger/40 bg-cg-danger/5',
  THREAT: 'border-cg-warning/40 bg-cg-warning/5',
  INCIDENT: 'border-cg-info/40 bg-cg-info/5',
  SYSTEM: 'border-cg-border bg-cg-surface-alt',
};

export default function Notifications() {
  const [notifications, setNotifications] = useState([]);
  const [loading, setLoading] = useState(true);

  const load = () => {
    setLoading(true);
    notificationService.getNotifications({ size: 30 }).then(({ data }) => setNotifications(data.content)).finally(() => setLoading(false));
  };

  useEffect(() => { load(); }, []);

  const handleMarkRead = async (id) => {
    await notificationService.markAsRead(id);
    load();
  };

  const handleMarkAllRead = async () => {
    await notificationService.markAllAsRead();
    load();
  };

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between flex-wrap gap-3">
        <div>
          <h2 className="text-lg font-semibold text-slate-100 flex items-center gap-2">
            <Bell size={20} className="text-cg-accent" /> Notifications
          </h2>
          <p className="text-sm text-slate-500 mt-1">Threat alerts, incident updates, and system notices.</p>
        </div>
        <button onClick={handleMarkAllRead} className="cg-btn-secondary flex items-center gap-2 text-sm">
          <CheckCheck size={16} /> Mark all as read
        </button>
      </div>

      <div className="space-y-2">
        {loading ? (
          Array.from({ length: 5 }).map((_, i) => <Skeleton key={i} className="h-20 w-full rounded-lg" />)
        ) : notifications.length === 0 ? (
          <EmptyState icon={Inbox} title="No notifications yet" message="Threat alerts and system notices will appear here." />
        ) : notifications.map((n) => (
          <div
            key={n.id}
            className={`border rounded-lg p-4 flex items-start justify-between gap-4 transition-all duration-150 hover:shadow-md hover:shadow-black/20 ${TYPE_COLORS[n.type] || TYPE_COLORS.SYSTEM} ${n.isRead ? 'opacity-60' : ''}`}
          >
            <div className="min-w-0">
              <p className="text-sm font-medium text-slate-200">{n.title}</p>
              <p className="text-sm text-slate-400 mt-0.5">{n.message}</p>
              <p className="text-xs text-slate-600 mt-1">{new Date(n.createdAt).toLocaleString()}</p>
            </div>
            {!n.isRead && (
              <button onClick={() => handleMarkRead(n.id)} className="text-xs text-cg-accent hover:underline whitespace-nowrap shrink-0">
                Mark read
              </button>
            )}
          </div>
        ))}
      </div>
    </div>
  );
}
