import { useEffect, useMemo, useRef, useState } from 'react';
import {
  Bell, ShieldAlert, FileWarning, CheckCircle2, UserPlus, KeyRound, Gauge, Info,
} from 'lucide-react';
import { notificationService } from '../../services/notificationService';
import { useEventStreamStatus } from '../../context/EventStreamContext';

const ICON_MAP = {
  threat: ShieldAlert,
  critical: ShieldAlert,
  'incident-created': FileWarning,
  'incident-closed': CheckCircle2,
  'user-created': UserPlus,
  'password-reset': KeyRound,
  'risk-score': Gauge,
  incident: FileWarning,
  system: Info,
};

const SEVERITY_STYLES = {
  LOW: 'text-cg-info border-cg-info/30 bg-cg-info/10',
  MEDIUM: 'text-cg-warning border-cg-warning/30 bg-cg-warning/10',
  HIGH: 'text-orange-400 border-orange-400/30 bg-orange-400/10',
  CRITICAL: 'text-cg-danger border-cg-danger/30 bg-cg-danger/10',
};

function timeAgo(dateString) {
  const seconds = Math.floor((Date.now() - new Date(dateString).getTime()) / 1000);
  if (seconds < 60) return 'just now';
  const minutes = Math.floor(seconds / 60);
  if (minutes < 60) return `${minutes}m ago`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `${hours}h ago`;
  return `${Math.floor(hours / 24)}d ago`;
}

export default function NotificationCenter() {
  const [items, setItems] = useState([]);
  const [unreadCount, setUnreadCount] = useState(0);
  const [open, setOpen] = useState(false);
  const panelRef = useRef(null);

  const loadInitial = () => {
    notificationService.getNotifications({ size: 15 }).then(({ data }) => setItems(data.content)).catch(() => {});
    notificationService.getUnreadCount().then(({ data }) => setUnreadCount(data.unreadCount)).catch(() => {});
  };

  useEffect(() => { loadInitial(); }, []);

  const { connected } = useEventStreamStatus();

  useEffect(() => {
    const handleNotification = (e) => {
      setItems((prev) => [e.detail, ...prev].slice(0, 20));
      setUnreadCount((prev) => prev + 1);
    };
    window.addEventListener('cg:notification', handleNotification);
    return () => window.removeEventListener('cg:notification', handleNotification);
  }, []);

  useEffect(() => {
    const handleClickOutside = (e) => {
      if (panelRef.current && !panelRef.current.contains(e.target)) setOpen(false);
    };
    const handleKeyDown = (e) => {
      if (e.key === 'Escape') setOpen(false);
    };
    document.addEventListener('mousedown', handleClickOutside);
    document.addEventListener('keydown', handleKeyDown);
    return () => {
      document.removeEventListener('mousedown', handleClickOutside);
      document.removeEventListener('keydown', handleKeyDown);
    };
  }, []);

  const handleToggle = () => setOpen((prev) => !prev);

  const groups = useMemo(() => {
    const isSameDay = (a, b) => a.toDateString() === b.toDateString();
    const now = new Date();
    const yesterday = new Date(now);
    yesterday.setDate(now.getDate() - 1);

    const buckets = { Today: [], Yesterday: [], Earlier: [] };
    items.forEach((item) => {
      const created = new Date(item.createdAt);
      if (isSameDay(created, now)) buckets.Today.push(item);
      else if (isSameDay(created, yesterday)) buckets.Yesterday.push(item);
      else buckets.Earlier.push(item);
    });
    return Object.entries(buckets).filter(([, list]) => list.length > 0);
  }, [items]);

  const handleMarkAllRead = async () => {
    await notificationService.markAllAsRead();
    setItems((prev) => prev.map((n) => ({ ...n, isRead: true })));
    setUnreadCount(0);
  };

  const handleItemClick = async (item) => {
    if (!item.isRead) {
      await notificationService.markAsRead(item.id);
      setItems((prev) => prev.map((n) => (n.id === item.id ? { ...n, isRead: true } : n)));
      setUnreadCount((prev) => Math.max(0, prev - 1));
    }
  };

  return (
    <div className="relative" ref={panelRef}>
      <button
        onClick={handleToggle}
        className="relative text-slate-400 hover:text-cg-accent transition"
        aria-label={`Notifications${unreadCount > 0 ? ` (${unreadCount} unread)` : ''}`}
        title={connected ? 'Live updates connected' : 'Reconnecting to live updates...'}
      >
        <Bell size={20} />
        <span
          className={`absolute -bottom-0.5 -right-0.5 w-2 h-2 rounded-full border border-cg-surface ${
            connected ? 'bg-cg-success' : 'bg-slate-500 animate-pulse'
          }`}
        />
        {unreadCount > 0 && (
          <span className="absolute -top-1.5 -right-1.5 bg-cg-danger text-white text-[10px] rounded-full h-4 min-w-4 px-1 flex items-center justify-center">
            {unreadCount > 99 ? '99+' : unreadCount}
          </span>
        )}
      </button>

      {open && (
        <div className="absolute right-0 mt-3 w-96 max-h-[28rem] overflow-y-auto cg-card !p-0 shadow-glow z-20">
          <div className="flex items-center justify-between px-4 py-3 border-b border-cg-border sticky top-0 bg-cg-surface">
            <h3 className="text-sm font-semibold text-slate-200">Notifications</h3>
            {unreadCount > 0 && (
              <button onClick={handleMarkAllRead} className="text-xs text-cg-accent hover:underline">
                Mark all read
              </button>
            )}
          </div>

          {items.length === 0 ? (
            <p className="text-sm text-slate-500 text-center py-8">No notifications yet.</p>
          ) : (
            groups.map(([groupLabel, groupItems]) => (
              <div key={groupLabel}>
                <p className="px-4 pt-3 pb-1 text-[10px] font-semibold uppercase tracking-wider text-slate-600 bg-cg-surface sticky top-[45px]">
                  {groupLabel}
                </p>
                <ul className="divide-y divide-cg-border">
                  {groupItems.map((item) => {
                    const Icon = ICON_MAP[item.icon] || Info;
                    const severityClass = SEVERITY_STYLES[item.severity] || 'text-slate-400 border-cg-border bg-cg-surface-alt';
                    return (
                      <li
                        key={item.id}
                        onClick={() => handleItemClick(item)}
                        className={`flex gap-3 px-4 py-3 cursor-pointer hover:bg-cg-surface-alt transition animate-fade-in ${item.isRead ? 'opacity-60' : ''}`}
                      >
                        <div className={`shrink-0 w-8 h-8 rounded-full border flex items-center justify-center ${severityClass}`}>
                          <Icon size={15} />
                        </div>
                        <div className="min-w-0 flex-1">
                          <div className="flex items-center justify-between gap-2">
                            <p className="text-sm font-medium text-slate-200 truncate">{item.title}</p>
                            {!item.isRead && <span className="w-1.5 h-1.5 rounded-full bg-cg-accent shrink-0" />}
                          </div>
                          <p className="text-xs text-slate-500 line-clamp-2">{item.message}</p>
                          <p className="text-[10px] text-slate-600 mt-1">{timeAgo(item.createdAt)}</p>
                        </div>
                      </li>
                    );
                  })}
                </ul>
              </div>
            ))
          )}
        </div>
      )}
    </div>
  );
}
