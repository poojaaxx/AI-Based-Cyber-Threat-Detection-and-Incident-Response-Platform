import { useEffect, useState } from 'react';
import { NavLink, Link } from 'react-router-dom';
import {
  LayoutDashboard, ShieldAlert, FileWarning, BookOpen, Activity, BarChart3, HeartPulse,
  FileBarChart, Bell, ScrollText, Settings, Bot, Users, ShieldCheck, FlaskConical, UserCircle2, X,
  ChevronsLeft, ChevronsRight, User,
} from 'lucide-react';
import { useAuth } from '../../context/AuthContext';

const NAV_ITEMS = [
  { to: '/dashboard', label: 'Dashboard', icon: LayoutDashboard },
  { to: '/threats', label: 'Threats', icon: ShieldAlert, userLabel: 'Personal Threat Alerts' },
  { to: '/simulate', label: 'Simulate Threat', icon: FlaskConical, roles: ['ROLE_ADMIN', 'ROLE_ANALYST'] },
  { to: '/incidents', label: 'Incidents', icon: FileWarning, roles: ['ROLE_ADMIN', 'ROLE_ANALYST'] },
  { to: '/analytics', label: 'Analytics', icon: BarChart3, roles: ['ROLE_ADMIN', 'ROLE_ANALYST'] },
  { to: '/threat-intel', label: 'Threat Intel', icon: BookOpen, roles: ['ROLE_ADMIN', 'ROLE_ANALYST'] },
  { to: '/monitoring', label: 'Monitoring', icon: Activity, roles: ['ROLE_ADMIN', 'ROLE_ANALYST'] },
  { to: '/security-health', label: 'Security Health', icon: HeartPulse, roles: ['ROLE_ADMIN', 'ROLE_ANALYST'] },
  { to: '/reports', label: 'Reports', icon: FileBarChart, roles: ['ROLE_ADMIN', 'ROLE_ANALYST'] },
  { to: '/notifications', label: 'Notifications', icon: Bell },
  { to: '/assistant', label: 'AI Assistant', icon: Bot },
  { to: '/audit-logs', label: 'Audit Logs', icon: ScrollText, roles: ['ROLE_ADMIN', 'ROLE_ANALYST'] },
  { to: '/users', label: 'Users', icon: Users, roles: ['ROLE_ADMIN'] },
  { to: '/settings', label: 'Settings', icon: Settings, roles: ['ROLE_ADMIN'] },
  { to: '/profile', label: 'Profile', icon: UserCircle2 },
];

const COLLAPSE_KEY = 'cg_sidebar_collapsed';

function getInitialCollapsed() {
  if (typeof window === 'undefined') return false;
  const stored = localStorage.getItem(COLLAPSE_KEY);
  if (stored !== null) return stored === 'true';
  // No saved preference yet: laptop widths (768-1199px) default to collapsed,
  // desktop (>=1200px) defaults to expanded, per the responsive spec.
  return window.innerWidth >= 768 && window.innerWidth < 1200;
}

export default function Sidebar({ open = false, onClose }) {
  const { user } = useAuth();
  const isPureUser = user?.roles?.length === 1 && user.roles.includes('ROLE_USER');
  const [collapsed, setCollapsed] = useState(getInitialCollapsed);
  const [tooltip, setTooltip] = useState(null);

  useEffect(() => {
    localStorage.setItem(COLLAPSE_KEY, String(collapsed));
  }, [collapsed]);

  const role = user?.roles?.[0]?.replace('ROLE_', '');

  // Tooltips render as a single fixed-position element (rather than a CSS
  // group-hover sibling) because the nav list needs overflow-y:auto for
  // scrolling, and per the CSS spec that forces overflow-x to compute as
  // "auto" too - which would clip an absolutely-positioned tooltip that
  // escapes the collapsed 80px-wide rail.
  const showTooltip = (e, label) => {
    if (!collapsed) return;
    const rect = e.currentTarget.getBoundingClientRect();
    setTooltip({ label, top: rect.top + rect.height / 2 });
  };
  const hideTooltip = () => setTooltip(null);

  return (
    <>
      {/* Mobile-only backdrop, shown behind the drawer while it's open */}
      {open && (
        <div
          className="fixed inset-0 bg-black/60 z-30 lg:hidden animate-fade-in"
          onClick={onClose}
          aria-hidden="true"
        />
      )}

      <aside
        className={`w-64 ${collapsed ? 'lg:w-20' : 'lg:w-64'} shrink-0 bg-cg-surface border-r border-cg-border flex flex-col h-screen
          fixed lg:sticky top-0 z-40 transition-[transform,width] duration-300 ease-in-out
          ${open ? 'translate-x-0' : '-translate-x-full'} lg:translate-x-0`}
      >
        <div className={`flex items-center gap-2 px-5 py-5 border-b border-cg-border ${collapsed ? 'lg:justify-center lg:px-0' : 'justify-between'}`}>
          <div className="flex items-center gap-2 min-w-0">
            <ShieldCheck className="text-cg-accent shrink-0" size={26} />
            <div className={collapsed ? 'lg:hidden' : 'min-w-0'}>
              <p className="font-bold text-slate-100 leading-tight truncate">CyberGuard</p>
              <p className="text-[10px] text-slate-500 uppercase tracking-widest">SOC Platform</p>
            </div>
          </div>
          <button
            onClick={onClose}
            className={`lg:hidden text-slate-400 hover:text-slate-200 transition-colors ${collapsed ? '' : ''}`}
            aria-label="Close navigation menu"
          >
            <X size={20} />
          </button>
        </div>

      <nav className="flex-1 overflow-y-auto py-4 px-3 space-y-1">
        {NAV_ITEMS.filter((item) => !item.roles || item.roles.some((r) => user?.roles?.includes(r))).map(
          ({ to, label, userLabel, icon: Icon }) => {
            const displayLabel = isPureUser && userLabel ? userLabel : label;
            return (
              <div
                key={to}
                className="relative"
                onMouseEnter={(e) => showTooltip(e, displayLabel)}
                onMouseLeave={hideTooltip}
              >
                <NavLink
                  to={to}
                  className={({ isActive }) =>
                    `group flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm font-medium transition-all duration-150 relative
                     focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-cg-accent/50 ${collapsed ? 'lg:justify-center lg:px-0' : ''} ${
                      isActive
                        ? 'bg-cg-accent/10 text-cg-accent border border-cg-accent/30 shadow-glow'
                        : 'text-slate-400 hover:text-slate-200 hover:bg-cg-surface-alt hover:translate-x-0.5 border border-transparent'
                    }`
                  }
                >
                  {({ isActive }) => (
                    <>
                      <span aria-hidden="true" className={`absolute left-0 top-1.5 bottom-1.5 w-0.5 rounded-full bg-cg-accent transition-opacity duration-150 ${isActive ? 'opacity-100' : 'opacity-0'} ${collapsed ? 'lg:hidden' : ''}`} />
                      <Icon size={18} className="shrink-0 transition-transform duration-150 group-hover:scale-110" />
                      <span className={collapsed ? 'lg:hidden' : 'truncate'}>{displayLabel}</span>
                    </>
                  )}
                </NavLink>
              </div>
            );
          }
        )}
      </nav>

      <div className={`border-t border-cg-border ${collapsed ? 'lg:px-2' : 'px-3'} py-3`}>
        <button
          onClick={() => setCollapsed((v) => !v)}
          className={`hidden lg:flex w-full items-center gap-2 rounded-lg px-3 py-2 text-xs font-medium text-slate-400
            hover:bg-cg-surface-alt hover:text-slate-200 transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-cg-accent/50
            ${collapsed ? 'justify-center' : ''}`}
          aria-label={collapsed ? 'Expand sidebar' : 'Collapse sidebar'}
          title={collapsed ? 'Expand sidebar' : 'Collapse sidebar'}
        >
          {collapsed ? <ChevronsRight size={16} /> : <><ChevronsLeft size={16} /><span>Collapse</span></>}
        </button>
      </div>

      <Link
        to="/profile"
        onClick={onClose}
        onMouseEnter={(e) => showTooltip(e, user?.fullName || 'Profile')}
        onMouseLeave={hideTooltip}
        className={`flex items-center gap-3 border-t border-cg-border px-4 py-4 hover:bg-cg-surface-alt transition-colors ${collapsed ? 'lg:justify-center lg:px-0' : ''}`}
      >
        <div className="w-8 h-8 rounded-full bg-cg-accent/15 text-cg-accent flex items-center justify-center shrink-0">
          <User size={16} />
        </div>
        <div className={`min-w-0 ${collapsed ? 'lg:hidden' : ''}`}>
          <p className="text-sm font-medium text-slate-200 truncate">{user?.fullName}</p>
          {role && <p className="text-[10px] uppercase tracking-wide text-slate-500 truncate">{role}</p>}
        </div>
      </Link>

      {tooltip && (
        <div
          className="hidden lg:block fixed z-50 pointer-events-none rounded-md border border-cg-border bg-cg-surface-alt px-2.5 py-1.5 text-xs font-medium text-slate-200 shadow-lg shadow-black/40 animate-fade-in"
          style={{ top: tooltip.top, left: 84, transform: 'translateY(-50%)' }}
          role="tooltip"
        >
          {tooltip.label}
        </div>
      )}
      </aside>
    </>
  );
}
