import { useEffect, useRef, useState } from 'react';
import { useNavigate, useLocation, Link } from 'react-router-dom';
import { LogOut, User, UserCircle2, ChevronDown, Settings as SettingsIcon, Menu } from 'lucide-react';
import { useAuth } from '../../context/AuthContext';
import { useEventStreamStatus } from '../../context/EventStreamContext';
import NotificationCenter from './NotificationCenter';

const BREADCRUMB_LABELS = {
  dashboard: 'Dashboard', threats: 'Threats', incidents: 'Incidents', analytics: 'Analytics',
  'threat-intel': 'Threat Intel', simulate: 'Simulate Threat', 'security-health': 'Security Health',
  reports: 'Reports', users: 'Users', profile: 'Profile', settings: 'Settings',
  notifications: 'Notifications', monitoring: 'Monitoring', 'audit-logs': 'Audit Logs', assistant: 'AI Assistant',
  investigation: 'Investigation',
};

function useBreadcrumb() {
  const { pathname } = useLocation();
  const segments = pathname.split('/').filter(Boolean);
  return segments.map((seg, i) => {
    const isId = /^[0-9a-f-]{6,}$/i.test(seg) && !BREADCRUMB_LABELS[seg];
    return { label: isId ? `#${seg.slice(0, 8)}` : (BREADCRUMB_LABELS[seg] || seg), last: i === segments.length - 1 };
  });
}

export default function Topbar({ title, onMenuClick }) {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const { connected } = useEventStreamStatus();
  const breadcrumb = useBreadcrumb();
  const [menuOpen, setMenuOpen] = useState(false);
  const menuRef = useRef(null);

  useEffect(() => {
    const handleClickOutside = (e) => {
      if (menuRef.current && !menuRef.current.contains(e.target)) setMenuOpen(false);
    };
    const handleKeyDown = (e) => {
      if (e.key === 'Escape') setMenuOpen(false);
    };
    document.addEventListener('mousedown', handleClickOutside);
    document.addEventListener('keydown', handleKeyDown);
    return () => {
      document.removeEventListener('mousedown', handleClickOutside);
      document.removeEventListener('keydown', handleKeyDown);
    };
  }, []);

  const handleLogout = async () => {
    await logout();
    navigate('/login');
  };

  const role = user?.roles?.[0]?.replace('ROLE_', '');
  const isAdmin = user?.roles?.includes('ROLE_ADMIN');

  return (
    <header className="h-16 border-b border-cg-border bg-cg-surface/80 backdrop-blur flex items-center justify-between px-4 sm:px-6 sticky top-0 z-10 gap-3">
      <div className="flex items-center gap-3 min-w-0">
        <button
          onClick={onMenuClick}
          className="lg:hidden text-slate-400 hover:text-slate-200 transition-colors shrink-0"
          aria-label="Toggle navigation menu"
        >
          <Menu size={22} />
        </button>
        <div className="min-w-0">
          <h1 className="text-lg font-semibold text-slate-100 truncate">{title}</h1>
          <nav aria-label="Breadcrumb" className="hidden sm:block">
            <ol className="flex items-center gap-1.5 text-xs text-slate-500">
              <li>CyberGuard</li>
              {breadcrumb.map((crumb, i) => (
                <li key={i} className="flex items-center gap-1.5">
                  <span aria-hidden="true">&rsaquo;</span>
                  <span className={crumb.last ? 'text-slate-400' : ''}>{crumb.label}</span>
                </li>
              ))}
            </ol>
          </nav>
        </div>
      </div>

      <div className="flex items-center gap-3 sm:gap-5">
        <div
          className="hidden md:flex items-center gap-1.5 rounded-full border border-cg-border bg-cg-surface-alt/60 px-2.5 py-1 text-[11px] font-medium text-slate-400"
          title={connected ? 'Live updates connected' : 'Reconnecting to live updates...'}
        >
          <span className="relative flex h-1.5 w-1.5">
            {connected && <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-cg-success opacity-75" />}
            <span className={`relative inline-flex rounded-full h-1.5 w-1.5 ${connected ? 'bg-cg-success' : 'bg-slate-500'}`} />
          </span>
          {connected ? 'Live' : 'Reconnecting'}
        </div>
        <NotificationCenter />

        <div className="relative" ref={menuRef}>
          <button
            onClick={() => setMenuOpen((v) => !v)}
            className="flex items-center gap-2 text-sm rounded-md px-1.5 py-1 hover:bg-cg-surface-alt transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-cg-accent/50"
            aria-haspopup="true"
            aria-expanded={menuOpen}
          >
            <div className="w-8 h-8 rounded-full bg-cg-accent/15 text-cg-accent flex items-center justify-center shrink-0">
              <User size={16} />
            </div>
            <div className="hidden sm:block leading-tight text-left">
              <p className="text-slate-200 font-medium">{user?.fullName}</p>
              {role && (
                <span className="inline-flex items-center px-1.5 py-px rounded text-[10px] font-semibold uppercase tracking-wide bg-cg-surface-alt text-slate-400 ring-1 ring-inset ring-white/10">
                  {role}
                </span>
              )}
            </div>
            <ChevronDown size={14} className={`text-slate-500 transition-transform duration-150 ${menuOpen ? 'rotate-180' : ''}`} />
          </button>

          {menuOpen && (
            <div className="absolute right-0 mt-2 w-56 cg-card !p-0 shadow-lg shadow-black/30 z-20 animate-fade-in overflow-hidden">
              <div className="px-4 py-3 border-b border-cg-border">
                <p className="text-sm font-medium text-slate-200 truncate">{user?.fullName}</p>
                <p className="text-xs text-slate-500 truncate">{user?.email}</p>
              </div>
              <div className="py-1">
                <Link
                  to="/profile"
                  onClick={() => setMenuOpen(false)}
                  className="flex items-center gap-2.5 px-4 py-2 text-sm text-slate-300 hover:bg-cg-surface-alt hover:text-cg-accent transition-colors"
                >
                  <UserCircle2 size={15} /> View Profile
                </Link>
                {isAdmin && (
                  <Link
                    to="/settings"
                    onClick={() => setMenuOpen(false)}
                    className="flex items-center gap-2.5 px-4 py-2 text-sm text-slate-300 hover:bg-cg-surface-alt hover:text-cg-accent transition-colors"
                  >
                    <SettingsIcon size={15} /> Settings
                  </Link>
                )}
                <button
                  onClick={handleLogout}
                  className="w-full flex items-center gap-2.5 px-4 py-2 text-sm text-slate-300 hover:bg-cg-surface-alt hover:text-cg-danger transition-colors"
                >
                  <LogOut size={15} /> Log Out
                </button>
              </div>
            </div>
          )}
        </div>
      </div>
    </header>
  );
}
