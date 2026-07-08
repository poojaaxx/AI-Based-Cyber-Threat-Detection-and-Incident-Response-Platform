import { useEffect, useState } from 'react';
import {
  HeartPulse, KeyRound, Lock, Ban, Users, Wifi, ShieldAlert, Gauge, Clock,
  Activity, ScrollText, Server, Database, Cpu, CheckCircle2, XCircle,
} from 'lucide-react';
import { PieChart, Pie, Cell, Tooltip, Legend, ResponsiveContainer } from 'recharts';
import StatCard from '../components/common/StatCard';
import { ChartSkeleton } from '../components/common/Skeleton';
import EmptyState from '../components/common/EmptyState';
import { securityHealthService } from '../services/securityHealthService';
import { PIE_COLORS, CHART_TOOLTIP_STYLE, RISK_LEVEL_COLOR } from '../utils/chartColors';

function timeAgo(dateString) {
  const seconds = Math.floor((Date.now() - new Date(dateString).getTime()) / 1000);
  if (seconds < 60) return 'just now';
  const minutes = Math.floor(seconds / 60);
  if (minutes < 60) return `${minutes}m ago`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `${hours}h ago`;
  return `${Math.floor(hours / 24)}d ago`;
}

function formatUptime(seconds) {
  const hours = Math.floor(seconds / 3600);
  const minutes = Math.floor((seconds % 3600) / 60);
  if (hours > 0) return `${hours}h ${minutes}m`;
  return `${minutes}m`;
}

function StatusPill({ label, status }) {
  const up = status === 'UP';
  return (
    <div className="flex items-center justify-between bg-cg-surface-alt border border-cg-border rounded-md px-3 py-2.5">
      <span className="text-sm text-slate-300">{label}</span>
      <span className={`flex items-center gap-1.5 text-xs font-semibold ${up ? 'text-cg-success' : 'text-cg-danger'}`}>
        {up ? <CheckCircle2 size={14} /> : <XCircle size={14} />}
        {status}
      </span>
    </div>
  );
}

function ActivityFeed({ title, icon: Icon, items, emptyMessage }) {
  return (
    <div className="cg-card">
      <h3 className="text-sm font-semibold text-slate-300 mb-3 flex items-center gap-2">
        <Icon size={15} className="text-cg-accent" /> {title}
      </h3>
      {!items || items.length === 0 ? (
        <EmptyState title="No recent activity" message={emptyMessage} />
      ) : (
        <div className="space-y-2 max-h-72 overflow-y-auto">
          {items.map((item) => (
            <div key={item.id} className="flex items-center justify-between border-b border-cg-border pb-2 last:border-0 text-sm">
              <div className="min-w-0">
                <p className="text-slate-200 truncate">{item.action || item.title}</p>
                <p className="text-xs text-slate-500 truncate">
                  {item.user ? `by ${item.user.username}` : item.message}
                </p>
              </div>
              <span className="text-xs text-slate-500 shrink-0 ml-2">{timeAgo(item.createdAt)}</span>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

export default function SecurityHealth() {
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    securityHealthService.getHealth()
      .then(({ data }) => setData(data))
      .catch(() => setError('Failed to load security health data.'))
      .finally(() => setLoading(false));
  }, []);

  if (loading) {
    return (
      <div className="space-y-6">
        <div className="grid grid-cols-1 md:grid-cols-3 xl:grid-cols-6 gap-4">
          {Array.from({ length: 6 }).map((_, i) => <ChartSkeleton key={i} height={90} title={false} />)}
        </div>
        <ChartSkeleton height={260} />
      </div>
    );
  }

  if (error || !data) {
    return <div className="text-cg-danger">{error || 'No data available.'}</div>;
  }

  const roleData = Object.entries(data.roleDistribution || {}).map(([name, value]) => ({ name, value }));

  return (
    <div className="space-y-6">
      <div>
        <h2 className="text-lg font-semibold text-slate-100 flex items-center gap-2">
          <HeartPulse size={20} className="text-cg-accent" /> Security Health
        </h2>
        <p className="text-sm text-slate-500 mt-1">
          Authentication health, account status, risk posture, and system reachability at a glance.
        </p>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-3 xl:grid-cols-6 gap-4">
        <StatCard label="Failed Logins (24h)" value={data.failedLoginAttempts24h} icon={KeyRound} accent="text-cg-warning" />
        <StatCard label="Locked Accounts" value={data.lockedAccounts} icon={Lock} accent="text-cg-danger" />
        <StatCard label="Disabled Accounts" value={data.disabledAccounts} icon={Ban} accent="text-slate-400" />
        <StatCard label="Active Users" value={data.activeUsers} icon={Users} accent="text-cg-success" />
        <StatCard label="Online Users" value={data.onlineUsers} icon={Wifi} accent="text-cg-info" />
        <StatCard label="Critical Threats" value={data.criticalThreatCount} icon={ShieldAlert} accent="text-cg-danger" />
      </div>

      <div className="grid grid-cols-1 md:grid-cols-3 xl:grid-cols-6 gap-4">
        <StatCard
          label="Enterprise Risk"
          value={data.averageRiskScore}
          icon={Gauge}
          accent={RISK_LEVEL_COLOR[data.riskLevel] || 'text-cg-accent'}
          hint={data.riskLevel}
        />
        <StatCard label="Avg Resolution" value={`${data.averageIncidentResolutionHours}h`} icon={Clock} accent="text-cg-info" />
        <StatCard label="Detection Rate" value={`${data.threatDetectionRatePerDay}/day`} icon={Activity} accent="text-cg-accent" />
        <StatCard label="Top Category" value={data.mostCommonThreatCategory} icon={ShieldAlert} accent="text-orange-400" />
        <StatCard label="Audit Events (24h)" value={data.auditEvents24h} icon={ScrollText} accent="text-cg-accent" />
        <StatCard label="Active JWT Sessions" value={data.jwtActiveTokens} icon={KeyRound} accent="text-cg-success" hint={`${data.jwtExpiringSoon} expiring in 24h`} />
      </div>

      <div className="grid grid-cols-1 xl:grid-cols-3 gap-4">
        <div className="cg-card">
          <h3 className="text-sm font-semibold text-slate-300 mb-3 flex items-center gap-2">
            <Server size={15} className="text-cg-accent" /> System Health
          </h3>
          <div className="space-y-2">
            <StatusPill label="Backend API" status={data.systemHealth.backendStatus} />
            <StatusPill label="Database" status={data.systemHealth.databaseStatus} />
            <StatusPill label="AI Service" status={data.systemHealth.aiServiceStatus} />
          </div>
          <p className="text-xs text-slate-500 mt-3 flex items-center gap-1.5">
            <Cpu size={12} /> Uptime: {formatUptime(data.systemHealth.uptimeSeconds)}
          </p>
        </div>

        <div className="cg-card xl:col-span-2">
          <h3 className="text-sm font-semibold text-slate-300 mb-3 flex items-center gap-2">
            <Database size={15} className="text-cg-accent" /> Role Distribution
          </h3>
          <ResponsiveContainer width="100%" height={200}>
            <PieChart>
              <Pie data={roleData} dataKey="value" nameKey="name" cx="50%" cy="50%" outerRadius={70} label>
                {roleData.map((entry, i) => <Cell key={entry.name} fill={PIE_COLORS[i % PIE_COLORS.length]} />)}
              </Pie>
              <Tooltip contentStyle={CHART_TOOLTIP_STYLE} />
              <Legend />
            </PieChart>
          </ResponsiveContainer>
        </div>
      </div>

      <div className="grid grid-cols-1 xl:grid-cols-3 gap-4">
        <ActivityFeed title="Recent User Activity" icon={Users} items={data.recentUserActivity} emptyMessage="No recent user actions recorded." />
        <ActivityFeed title="Recent Admin Activity" icon={ShieldAlert} items={data.recentAdminActivity} emptyMessage="No recent admin actions recorded." />
        <ActivityFeed title="Recent Security Events" icon={HeartPulse} items={data.recentSecurityEvents} emptyMessage="No high/critical severity events recently." />
      </div>
    </div>
  );
}
