import { useEffect, useState } from 'react';
import { ShieldAlert, AlertTriangle, FileWarning, Activity, Bot, RefreshCw, Shield, Flame, Inbox } from 'lucide-react';
import {
  LineChart, Line, PieChart, Pie, Cell, BarChart, Bar,
  XAxis, YAxis, CartesianGrid, Tooltip, Legend, ResponsiveContainer,
  RadarChart, Radar, PolarGrid, PolarAngleAxis, PolarRadiusAxis,
} from 'recharts';
import StatCard from '../components/common/StatCard';
import { SeverityBadge, StatusBadge } from '../components/common/Badge';
import { ChartSkeleton, Skeleton } from '../components/common/Skeleton';
import EmptyState from '../components/common/EmptyState';
import { dashboardService } from '../services/dashboardService';
import { useCountUp } from '../hooks/useCountUp';
import { SEVERITY_COLORS, PIE_COLORS, RISK_LEVEL_COLOR, CHART_TOOLTIP_STYLE, CHART_AXIS_PROPS } from '../utils/chartColors';

export default function Dashboard() {
  const [summary, setSummary] = useState(null);
  const [loading, setLoading] = useState(true);
  const [riskScore, setRiskScore] = useState(null);
  const [securitySummary, setSecuritySummary] = useState(null);
  const [summaryLoading, setSummaryLoading] = useState(false);
  const [lastUpdated, setLastUpdated] = useState(null);

  const loadSummary = () => {
    dashboardService.getSummary().then(({ data }) => setSummary(data)).finally(() => setLoading(false));
  };

  const loadRiskWidgets = () => {
    dashboardService.getRiskScore().then(({ data }) => setRiskScore(data)).catch(() => {});
    setSummaryLoading(true);
    dashboardService.getSecuritySummary()
      .then(({ data }) => setSecuritySummary(data))
      .catch(() => {})
      .finally(() => setSummaryLoading(false));
  };

  useEffect(() => {
    loadSummary();
    loadRiskWidgets();
  }, []);

  // Real-time: whenever the backend signals a new threat, incident, or risk-score
  // change, silently refetch the same REST endpoints above rather than duplicating
  // any computation on the frontend. The event itself arrives via the shared
  // EventStreamProvider connection (see context/EventStreamContext.jsx).
  useEffect(() => {
    const handleDashboardUpdate = () => {
      loadSummary();
      loadRiskWidgets();
      setLastUpdated(new Date());
    };
    window.addEventListener('cg:dashboard-update', handleDashboardUpdate);
    return () => window.removeEventListener('cg:dashboard-update', handleDashboardUpdate);
  }, []);

  const securityScoreDisplay = useCountUp(summary?.securityScore);

  if (loading) {
    return (
      <div className="space-y-6">
        <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-4 gap-4">
          {Array.from({ length: 6 }).map((_, i) => <ChartSkeleton key={i} height={80} title={false} />)}
        </div>
        <ChartSkeleton height={140} />
        <ChartSkeleton height={280} />
        <div className="grid grid-cols-1 xl:grid-cols-3 gap-4">
          <ChartSkeleton height={260} />
          <ChartSkeleton height={260} />
        </div>
      </div>
    );
  }
  if (!summary) {
    return (
      <EmptyState
        icon={AlertTriangle}
        title="Failed to load dashboard"
        message="We couldn't reach the dashboard summary service. Try refreshing the page."
        actionLabel="Retry"
        onAction={() => window.location.reload()}
      />
    );
  }

  const radarData = riskScore ? [
    { category: 'Network', value: riskScore.networkRisk },
    { category: 'Host', value: riskScore.hostRisk },
    { category: 'Identity', value: riskScore.identityRisk },
    { category: 'Application', value: riskScore.applicationRisk },
    { category: 'Data', value: riskScore.dataRisk },
  ] : [];

  const severityData = Object.entries(summary.threatsBySeverity || {}).map(([name, value]) => ({ name, value }));
  const typeData = Object.entries(summary.threatsByType || {}).map(([name, value]) => ({ name, value }));
  const incidentStatusData = Object.entries(summary.incidentsByStatus || {}).map(([name, value]) => ({ name, value }));

  const scoreColor = summary.securityScore >= 80 ? 'text-cg-success' : summary.securityScore >= 50 ? 'text-cg-warning' : 'text-cg-danger';

  return (
    <div className="space-y-6">
      <div className="flex items-center gap-2 text-xs text-slate-500">
        <span className="relative flex h-2 w-2">
          <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-cg-accent opacity-75" />
          <span className="relative inline-flex rounded-full h-2 w-2 bg-cg-accent" />
        </span>
        Live
        {lastUpdated && <span>&middot; last update {lastUpdated.toLocaleTimeString()}</span>}
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-4 gap-4">
        <div className="cg-card cg-card-hover cg-card-top-accent flex flex-col items-center justify-center">
          <p className="text-xs uppercase tracking-wide text-slate-400 mb-2">Security Score</p>
          <p className={`text-4xl font-bold tabular-nums ${scoreColor}`}>{securityScoreDisplay}</p>
          <p className="text-xs text-slate-500 mt-1">out of 100</p>
        </div>
        <StatCard label="Total Threats" value={summary.totalThreats} icon={Shield} accent="text-cg-info" />
        <StatCard label="Active Threats" value={summary.activeThreats} icon={ShieldAlert} accent="text-cg-warning" topAccent="warning" />
        <StatCard label="Critical Threats" value={summary.criticalThreats} icon={AlertTriangle} accent="text-cg-danger" topAccent="critical" />
        <StatCard label="High Severity Threats" value={summary.highSeverityThreats} icon={Flame} accent="text-orange-400" topAccent="high" />
        <StatCard label="Open Incidents" value={summary.openIncidents} icon={FileWarning} accent="text-cg-info" />
        <StatCard label="Threats Today" value={summary.totalThreatsToday} icon={Activity} accent="text-cg-accent" topAccent="accent" />
      </div>

      <div className="cg-card">
        <div className="flex items-center justify-between mb-3">
          <div className="flex items-center gap-2">
            <Bot size={18} className="text-cg-accent" />
            <h3 className="text-sm font-semibold text-slate-300">AI Security Summary</h3>
          </div>
          <button onClick={loadRiskWidgets} disabled={summaryLoading} className="text-slate-400 hover:text-cg-accent transition" title="Regenerate">
            <RefreshCw size={16} className={summaryLoading ? 'animate-spin' : ''} />
          </button>
        </div>
        {securitySummary ? (
          <>
            <div className="flex items-center gap-2 mb-2">
              <span className="text-xs uppercase tracking-wide text-slate-500">Posture:</span>
              <span className={`text-sm font-semibold ${RISK_LEVEL_COLOR[riskScore?.riskLevel] || 'text-slate-300'}`}>
                {securitySummary.postureLevel}
              </span>
            </div>
            <p className="text-sm text-slate-300 leading-relaxed">{securitySummary.summary}</p>
          </>
        ) : (
          <div className="space-y-2">
            <Skeleton className="h-3 w-1/3" />
            <Skeleton className="h-3 w-full" />
            <Skeleton className="h-3 w-2/3" />
          </div>
        )}
      </div>

      <div className="cg-card">
        <h3 className="text-sm font-semibold text-slate-300 mb-4">Enterprise Risk Score</h3>
        {riskScore ? (
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4 items-center">
            <ResponsiveContainer width="100%" height={260}>
              <RadarChart data={radarData}>
                <PolarGrid stroke="#1f2937" />
                <PolarAngleAxis dataKey="category" stroke="#94a3b8" fontSize={12} />
                <PolarRadiusAxis angle={30} domain={[0, 100]} stroke="#1f2937" fontSize={10} />
                <Radar dataKey="value" stroke="#00e5c7" fill="#00e5c7" fillOpacity={0.35} />
                <Tooltip contentStyle={CHART_TOOLTIP_STYLE} />
              </RadarChart>
            </ResponsiveContainer>
            <div className="space-y-3">
              <div className="text-center md:text-left">
                <p className="text-xs uppercase tracking-wide text-slate-400">Overall Risk</p>
                <p className={`text-3xl font-bold ${RISK_LEVEL_COLOR[riskScore.riskLevel] || 'text-slate-200'}`}>
                  {riskScore.overallRisk}<span className="text-base text-slate-500">/100</span>
                </p>
                <p className={`text-xs font-semibold ${RISK_LEVEL_COLOR[riskScore.riskLevel] || 'text-slate-400'}`}>{riskScore.riskLevel}</p>
              </div>
              {[
                ['Network', riskScore.networkRisk], ['Host', riskScore.hostRisk], ['Identity', riskScore.identityRisk],
                ['Application', riskScore.applicationRisk], ['Data', riskScore.dataRisk],
              ].map(([label, value]) => (
                <div key={label} className="flex items-center justify-between text-xs">
                  <span className="text-slate-400">{label}</span>
                  <span className="text-slate-300 font-mono">{value}/100</span>
                </div>
              ))}
            </div>
          </div>
        ) : (
          <ChartSkeleton height={200} title={false} />
        )}
      </div>

      <div className="grid grid-cols-1 xl:grid-cols-3 gap-4">
        <div className="cg-card xl:col-span-2">
          <h3 className="text-sm font-semibold text-slate-300 mb-4">Threat Trend (Last 7 Days)</h3>
          <ResponsiveContainer width="100%" height={260}>
            <LineChart data={summary.threatTrend}>
              <CartesianGrid strokeDasharray="3 3" stroke="#1f2937" />
              <XAxis dataKey="date" {...CHART_AXIS_PROPS} />
              <YAxis allowDecimals={false} {...CHART_AXIS_PROPS} />
              <Tooltip contentStyle={CHART_TOOLTIP_STYLE} />
              <Line type="monotone" dataKey="count" stroke="#00e5c7" strokeWidth={2} dot={{ r: 3 }} animationDuration={600} />
            </LineChart>
          </ResponsiveContainer>
        </div>

        <div className="cg-card">
          <h3 className="text-sm font-semibold text-slate-300 mb-4">Threats by Severity</h3>
          <ResponsiveContainer width="100%" height={260}>
            <PieChart>
              <Pie data={severityData} dataKey="value" nameKey="name" cx="50%" cy="50%" outerRadius={85} label animationDuration={600}>
                {severityData.map((entry) => (
                  <Cell key={entry.name} fill={SEVERITY_COLORS[entry.name] || '#64748b'} />
                ))}
              </Pie>
              <Tooltip contentStyle={CHART_TOOLTIP_STYLE} />
              <Legend />
            </PieChart>
          </ResponsiveContainer>
        </div>
      </div>

      <div className="grid grid-cols-1 xl:grid-cols-2 gap-4">
        <div className="cg-card">
          <h3 className="text-sm font-semibold text-slate-300 mb-4">Threats by Type</h3>
          <ResponsiveContainer width="100%" height={260}>
            <BarChart data={typeData} layout="vertical" margin={{ left: 20 }}>
              <CartesianGrid strokeDasharray="3 3" stroke="#1f2937" />
              <XAxis type="number" allowDecimals={false} {...CHART_AXIS_PROPS} />
              <YAxis dataKey="name" type="category" width={110} fontSize={11} stroke="#64748b" />
              <Tooltip contentStyle={CHART_TOOLTIP_STYLE} />
              <Bar dataKey="value" fill="#00e5c7" radius={[0, 4, 4, 0]} animationDuration={600} />
            </BarChart>
          </ResponsiveContainer>
        </div>

        <div className="cg-card">
          <h3 className="text-sm font-semibold text-slate-300 mb-4">Incidents by Status</h3>
          <ResponsiveContainer width="100%" height={260}>
            <PieChart>
              <Pie data={incidentStatusData} dataKey="value" nameKey="name" cx="50%" cy="50%" outerRadius={85} label animationDuration={600}>
                {incidentStatusData.map((entry, i) => (
                  <Cell key={entry.name} fill={PIE_COLORS[i % PIE_COLORS.length]} />
                ))}
              </Pie>
              <Tooltip contentStyle={CHART_TOOLTIP_STYLE} />
              <Legend />
            </PieChart>
          </ResponsiveContainer>
        </div>
      </div>

      <div className="grid grid-cols-1 xl:grid-cols-2 gap-4">
        <div className="cg-card">
          <h3 className="text-sm font-semibold text-slate-300 mb-4">Recent Threats (AI Predictions)</h3>
          <div className="space-y-2 max-h-80 overflow-y-auto">
            {summary.recentThreats?.length ? summary.recentThreats.map((t) => (
              <div key={t.id} className="flex items-center justify-between border-b border-cg-border pb-2 last:border-0">
                <div>
                  <p className="text-sm text-slate-200">{t.threatType}</p>
                  <p className="text-xs text-slate-500">{t.sourceIp} &rarr; {t.destinationIp}</p>
                </div>
                <div className="flex items-center gap-2">
                  <span className="text-xs text-slate-500">{Number(t.confidenceScore).toFixed(1)}%</span>
                  <SeverityBadge severity={t.severity} />
                </div>
              </div>
            )) : <EmptyState icon={Inbox} title="No threats yet" message="Detected threats will appear here in real time." />}
          </div>
        </div>

        <div className="cg-card">
          <h3 className="text-sm font-semibold text-slate-300 mb-4">Recent Incidents</h3>
          <div className="space-y-2 max-h-80 overflow-y-auto">
            {summary.recentIncidents?.length ? summary.recentIncidents.map((i) => (
              <div key={i.id} className="flex items-center justify-between border-b border-cg-border pb-2 last:border-0">
                <div>
                  <p className="text-sm text-slate-200">{i.incidentNumber}</p>
                  <p className="text-xs text-slate-500">{i.title}</p>
                </div>
                <StatusBadge status={i.status} />
              </div>
            )) : <EmptyState icon={FileWarning} title="No incidents yet" message="Incidents created or auto-generated from threats will appear here." />}
          </div>
        </div>
      </div>
    </div>
  );
}
