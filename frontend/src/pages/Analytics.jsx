import { useEffect, useMemo, useState } from 'react';
import {
  BarChart3, TrendingUp, TrendingDown, Target, Crosshair, Users, Clock, ShieldCheck,
} from 'lucide-react';
import {
  LineChart, Line, AreaChart, Area, PieChart, Pie, Cell, BarChart, Bar,
  XAxis, YAxis, CartesianGrid, Tooltip, Legend, ResponsiveContainer,
} from 'recharts';
import StatCard from '../components/common/StatCard';
import RiskGauge from '../components/common/RiskGauge';
import { ChartSkeleton } from '../components/common/Skeleton';
import EmptyState from '../components/common/EmptyState';
import { analyticsService } from '../services/analyticsService';
import { dashboardService } from '../services/dashboardService';
import { SEVERITY_COLORS, PIE_COLORS, CHART_TOOLTIP_STYLE, CHART_AXIS_PROPS } from '../utils/chartColors';

const TREND_TABS = [
  { key: 'daily', label: 'Daily (30d)' },
  { key: 'weekly', label: 'Weekly (12w)' },
  { key: 'monthly', label: 'Monthly (12m)' },
];

export default function Analytics() {
  const [data, setData] = useState(null);
  const [riskScore, setRiskScore] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [trendTab, setTrendTab] = useState('daily');

  useEffect(() => {
    Promise.all([analyticsService.getOverview(), dashboardService.getRiskScore()])
      .then(([overviewRes, riskRes]) => {
        setData(overviewRes.data);
        setRiskScore(riskRes.data);
      })
      .catch(() => setError('Failed to load analytics.'))
      .finally(() => setLoading(false));
  }, []);

  const severityData = useMemo(
    () => Object.entries(data?.severityDistribution || {}).map(([name, value]) => ({ name, value })),
    [data]
  );
  const categoryData = useMemo(
    () => Object.entries(data?.categoryDistribution || {}).map(([name, value]) => ({ name, value })),
    [data]
  );
  const riskDistData = useMemo(
    () => Object.entries(data?.riskDistribution || {}).map(([name, value]) => ({ name, value })),
    [data]
  );
  const trendData = useMemo(() => {
    if (!data) return [];
    if (trendTab === 'weekly') return data.weeklyTrend.map((t) => ({ label: t.label, count: t.count }));
    if (trendTab === 'monthly') return data.monthlyTrend.map((t) => ({ label: t.label, count: t.count }));
    return data.dailyTrend.map((t) => ({ label: t.date, count: t.count }));
  }, [data, trendTab]);
  const resolutionData = useMemo(
    () => Object.entries(data?.resolutionTimeBySeverity || {}).map(([name, value]) => ({ name, value })),
    [data]
  );

  if (loading) {
    return (
      <div className="space-y-6">
        <div className="grid grid-cols-1 xl:grid-cols-3 gap-4">
          <ChartSkeleton height={220} />
          <ChartSkeleton height={220} />
          <ChartSkeleton height={220} />
        </div>
        <ChartSkeleton height={280} />
        <div className="grid grid-cols-1 xl:grid-cols-2 gap-4">
          <ChartSkeleton />
          <ChartSkeleton />
        </div>
      </div>
    );
  }

  if (error) {
    return <div className="text-cg-danger">{error}</div>;
  }

  if (!data || data.totalThreatsAnalyzed === 0) {
    return (
      <EmptyState
        icon={BarChart3}
        title="No analytics data yet"
        message="Run a few threat detections (e.g. via Simulate Threat) to start generating enterprise analytics."
        actionLabel="Go to Simulate Threat"
        actionTo="/simulate"
      />
    );
  }

  const growthPositive = data.threatGrowthPercent >= 0;

  return (
    <div className="space-y-6">
      <div>
        <h2 className="text-lg font-semibold text-slate-100 flex items-center gap-2">
          <BarChart3 size={20} className="text-cg-accent" /> Enterprise Analytics
        </h2>
        <p className="text-sm text-slate-500 mt-1">
          Trends, risk posture, and detection performance derived from the full threat and incident history.
        </p>
      </div>

      <div className="grid grid-cols-1 xl:grid-cols-3 gap-4">
        <div className="cg-card flex items-center justify-center">
          <RiskGauge score={riskScore?.overallRisk ?? 0} level={riskScore?.riskLevel ?? 'LOW'} lastUpdated={new Date()} />
        </div>

        <div className="grid grid-cols-2 gap-4 xl:col-span-2">
          <StatCard
            label="Detection Accuracy"
            value={`${data.detectionAccuracyPercent}%`}
            icon={ShieldCheck}
            accent="text-cg-success"
            hint={`${data.falsePositiveCount} false positives of ${data.totalThreatsAnalyzed}`}
          />
          <StatCard
            label="Threat Growth (7d)"
            value={`${growthPositive ? '+' : ''}${data.threatGrowthPercent}%`}
            icon={growthPositive ? TrendingUp : TrendingDown}
            accent={growthPositive ? 'text-cg-danger' : 'text-cg-success'}
            hint="vs. previous 7 days"
          />
          <StatCard
            label="Avg Resolution Time"
            value={`${data.avgResolutionTimeHours}h`}
            icon={Clock}
            accent="text-cg-info"
            hint="across resolved incidents"
          />
          <StatCard
            label="Total Threats Analyzed"
            value={data.totalThreatsAnalyzed}
            icon={Target}
            accent="text-cg-accent"
          />
        </div>
      </div>

      <div className="cg-card">
        <div className="flex items-center justify-between mb-4 flex-wrap gap-2">
          <h3 className="text-sm font-semibold text-slate-300">Threat Trend</h3>
          <div className="flex gap-1 bg-cg-surface-alt rounded-md p-1">
            {TREND_TABS.map((tab) => (
              <button
                key={tab.key}
                onClick={() => setTrendTab(tab.key)}
                className={`text-xs px-3 py-1 rounded transition ${
                  trendTab === tab.key ? 'bg-cg-accent text-cg-bg font-semibold' : 'text-slate-400 hover:text-slate-200'
                }`}
              >
                {tab.label}
              </button>
            ))}
          </div>
        </div>
        <ResponsiveContainer width="100%" height={260}>
          <AreaChart data={trendData}>
            <defs>
              <linearGradient id="trendFill" x1="0" y1="0" x2="0" y2="1">
                <stop offset="5%" stopColor="#00e5c7" stopOpacity={0.4} />
                <stop offset="95%" stopColor="#00e5c7" stopOpacity={0} />
              </linearGradient>
            </defs>
            <CartesianGrid strokeDasharray="3 3" stroke="#1f2937" />
            <XAxis dataKey="label" {...CHART_AXIS_PROPS} />
            <YAxis allowDecimals={false} {...CHART_AXIS_PROPS} />
            <Tooltip contentStyle={CHART_TOOLTIP_STYLE} />
            <Area type="monotone" dataKey="count" stroke="#00e5c7" strokeWidth={2} fill="url(#trendFill)" animationDuration={600} />
          </AreaChart>
        </ResponsiveContainer>
      </div>

      <div className="cg-card">
        <h3 className="text-sm font-semibold text-slate-300 mb-4">Enterprise Risk Trend (30 Days)</h3>
        <ResponsiveContainer width="100%" height={240}>
          <LineChart data={data.riskTrend}>
            <CartesianGrid strokeDasharray="3 3" stroke="#1f2937" />
            <XAxis dataKey="date" {...CHART_AXIS_PROPS} />
            <YAxis domain={[0, 100]} {...CHART_AXIS_PROPS} />
            <Tooltip contentStyle={CHART_TOOLTIP_STYLE} formatter={(value, name, props) => [`${value} (${props.payload.riskLevel})`, 'Risk']} />
            <Line type="monotone" dataKey="overallRisk" stroke="#a855f7" strokeWidth={2} dot={false} animationDuration={600} />
          </LineChart>
        </ResponsiveContainer>
      </div>

      <div className="grid grid-cols-1 xl:grid-cols-3 gap-4">
        <div className="cg-card">
          <h3 className="text-sm font-semibold text-slate-300 mb-4">Threats by Severity</h3>
          <ResponsiveContainer width="100%" height={240}>
            <PieChart>
              <Pie data={severityData} dataKey="value" nameKey="name" cx="50%" cy="50%" outerRadius={80} label animationDuration={600}>
                {severityData.map((entry) => <Cell key={entry.name} fill={SEVERITY_COLORS[entry.name] || '#64748b'} />)}
              </Pie>
              <Tooltip contentStyle={CHART_TOOLTIP_STYLE} />
              <Legend />
            </PieChart>
          </ResponsiveContainer>
        </div>

        <div className="cg-card">
          <h3 className="text-sm font-semibold text-slate-300 mb-4">Threats by Category</h3>
          <ResponsiveContainer width="100%" height={240}>
            <BarChart data={categoryData} layout="vertical" margin={{ left: 20 }}>
              <CartesianGrid strokeDasharray="3 3" stroke="#1f2937" />
              <XAxis type="number" allowDecimals={false} {...CHART_AXIS_PROPS} />
              <YAxis dataKey="name" type="category" width={100} fontSize={10} stroke="#64748b" />
              <Tooltip contentStyle={CHART_TOOLTIP_STYLE} />
              <Bar dataKey="value" fill="#00e5c7" radius={[0, 4, 4, 0]} animationDuration={600} />
            </BarChart>
          </ResponsiveContainer>
        </div>

        <div className="cg-card">
          <h3 className="text-sm font-semibold text-slate-300 mb-4">Risk Distribution</h3>
          <ResponsiveContainer width="100%" height={240}>
            <PieChart>
              <Pie data={riskDistData} dataKey="value" nameKey="name" cx="50%" cy="50%" outerRadius={80} label animationDuration={600}>
                {riskDistData.map((entry) => <Cell key={entry.name} fill={SEVERITY_COLORS[entry.name] || '#64748b'} />)}
              </Pie>
              <Tooltip contentStyle={CHART_TOOLTIP_STYLE} />
              <Legend />
            </PieChart>
          </ResponsiveContainer>
        </div>
      </div>

      <div className="grid grid-cols-1 xl:grid-cols-2 gap-4">
        <div className="cg-card">
          <h3 className="text-sm font-semibold text-slate-300 mb-4 flex items-center gap-2">
            <Crosshair size={14} className="text-cg-accent" /> MITRE ATT&amp;CK Technique Distribution
          </h3>
          {data.mitreDistribution.length === 0 ? (
            <EmptyState title="No MITRE mappings yet" message="Detected threats will map to MITRE ATT&CK techniques automatically." />
          ) : (
            <ResponsiveContainer width="100%" height={240}>
              <BarChart data={data.mitreDistribution} layout="vertical" margin={{ left: 20 }}>
                <CartesianGrid strokeDasharray="3 3" stroke="#1f2937" />
                <XAxis type="number" allowDecimals={false} {...CHART_AXIS_PROPS} />
                <YAxis dataKey="techniqueId" type="category" width={60} fontSize={11} stroke="#64748b" />
                <Tooltip contentStyle={CHART_TOOLTIP_STYLE}
                  formatter={(value, name, props) => [value, props.payload.name]} />
                <Bar dataKey="count" fill="#a855f7" radius={[0, 4, 4, 0]} animationDuration={600} />
              </BarChart>
            </ResponsiveContainer>
          )}
        </div>

        <div className="cg-card">
          <h3 className="text-sm font-semibold text-slate-300 mb-4">Incident Resolution Time by Severity (hrs)</h3>
          <ResponsiveContainer width="100%" height={240}>
            <BarChart data={resolutionData}>
              <CartesianGrid strokeDasharray="3 3" stroke="#1f2937" />
              <XAxis dataKey="name" {...CHART_AXIS_PROPS} />
              <YAxis allowDecimals={false} {...CHART_AXIS_PROPS} />
              <Tooltip contentStyle={CHART_TOOLTIP_STYLE} />
              <Bar dataKey="value" radius={[4, 4, 0, 0]} animationDuration={600}>
                {resolutionData.map((entry) => <Cell key={entry.name} fill={SEVERITY_COLORS[entry.name] || '#64748b'} />)}
              </Bar>
            </BarChart>
          </ResponsiveContainer>
        </div>
      </div>

      <div className="grid grid-cols-1 xl:grid-cols-3 gap-4">
        <div className="cg-card">
          <h3 className="text-sm font-semibold text-slate-300 mb-4">Top Attack Sources</h3>
          {data.topAttackSources.length === 0 ? <EmptyState title="No source data" /> : (
            <ul className="space-y-2">
              {data.topAttackSources.map((entry, i) => (
                <li key={entry.label} className="flex items-center justify-between text-sm">
                  <span className="font-mono text-slate-300">{entry.label}</span>
                  <span className="flex items-center gap-2">
                    <span className="h-1.5 rounded-full bg-cg-danger" style={{ width: `${20 + entry.count * 8}px` }} />
                    <span className="text-slate-500 text-xs">{entry.count}</span>
                  </span>
                </li>
              ))}
            </ul>
          )}
        </div>

        <div className="cg-card">
          <h3 className="text-sm font-semibold text-slate-300 mb-4">Top Target Assets</h3>
          {data.topTargetAssets.length === 0 ? <EmptyState title="No target data" /> : (
            <ul className="space-y-2">
              {data.topTargetAssets.map((entry) => (
                <li key={entry.label} className="flex items-center justify-between text-sm">
                  <span className="font-mono text-slate-300">{entry.label}</span>
                  <span className="flex items-center gap-2">
                    <span className="h-1.5 rounded-full bg-cg-info" style={{ width: `${20 + entry.count * 8}px` }} />
                    <span className="text-slate-500 text-xs">{entry.count}</span>
                  </span>
                </li>
              ))}
            </ul>
          )}
        </div>

        <div className="cg-card">
          <h3 className="text-sm font-semibold text-slate-300 mb-4 flex items-center gap-2">
            <Users size={14} className="text-cg-accent" /> Most Active Analysts
          </h3>
          {data.mostActiveAnalysts.length === 0 ? (
            <EmptyState title="No assignments yet" message="Assign incidents to analysts to see activity here." />
          ) : (
            <ul className="space-y-2">
              {data.mostActiveAnalysts.map((entry) => (
                <li key={entry.label} className="flex items-center justify-between text-sm">
                  <span className="text-slate-300">{entry.label}</span>
                  <span className="cg-badge bg-cg-accent/15 text-cg-accent">{entry.count} incidents</span>
                </li>
              ))}
            </ul>
          )}
        </div>
      </div>
    </div>
  );
}
