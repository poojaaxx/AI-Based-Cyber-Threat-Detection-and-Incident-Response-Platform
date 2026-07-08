import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  FlaskConical, Loader2, ShieldCheck, ExternalLink, History, Search, Trash2, Eye, Download,
} from 'lucide-react';
import { SeverityBadge, StatusBadge } from '../components/common/Badge';
import ExplainAiPanel from '../components/common/ExplainAiPanel';
import Modal from '../components/common/Modal';
import EmptyState from '../components/common/EmptyState';
import { Skeleton } from '../components/common/Skeleton';
import { simulationService } from '../services/simulationService';
import { reportService } from '../services/reportService';

const IPV4_RE = /^(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)(\.(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)){3}$/;
const IPV6_RE = /^(([0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}|([0-9a-fA-F]{1,4}:){1,7}:|([0-9a-fA-F]{1,4}:){1,6}:[0-9a-fA-F]{1,4}|([0-9a-fA-F]{1,4}:){1,5}(:[0-9a-fA-F]{1,4}){1,2}|([0-9a-fA-F]{1,4}:){1,4}(:[0-9a-fA-F]{1,4}){1,3}|([0-9a-fA-F]{1,4}:){1,3}(:[0-9a-fA-F]{1,4}){1,4}|([0-9a-fA-F]{1,4}:){1,2}(:[0-9a-fA-F]{1,4}){1,5}|[0-9a-fA-F]{1,4}:((:[0-9a-fA-F]{1,4}){1,6})|:((:[0-9a-fA-F]{1,4}){1,7}|:)|::(ffff(:0{1,4})?:)?((25[0-5]|(2[0-4]|1?[0-9])?[0-9])\.){3}(25[0-5]|(2[0-4]|1?[0-9])?[0-9])|([0-9a-fA-F]{1,4}:){1,4}:((25[0-5]|(2[0-4]|1?[0-9])?[0-9])\.){3}(25[0-5]|(2[0-4]|1?[0-9])?[0-9]))$/;
const isValidIp = (value) => IPV4_RE.test(value.trim()) || IPV6_RE.test(value.trim());

// Mirrors the backend's SimulationRequest validation exactly, so obviously invalid
// input never reaches the network - the backend re-validates everything regardless.
function validateSimulationForm(form) {
  const errors = {};
  if (!form.sourceIp.trim()) errors.sourceIp = 'Source IP is required';
  else if (!isValidIp(form.sourceIp)) errors.sourceIp = 'Enter a valid IPv4 or IPv6 address';

  if (!form.destinationIp.trim()) errors.destinationIp = 'Destination IP is required';
  else if (!isValidIp(form.destinationIp)) errors.destinationIp = 'Enter a valid IPv4 or IPv6 address';

  if (form.port !== '') {
    const port = Number(form.port);
    if (!Number.isInteger(port) || port < 1 || port > 65535) errors.port = 'Port must be between 1 and 65535';
  }
  if (form.packetSize !== '') {
    const size = Number(form.packetSize);
    if (!Number.isFinite(size) || size < 0) errors.packetSize = 'Packet size cannot be negative';
    else if (size > 100_000_000) errors.packetSize = 'Packet size is unrealistically large';
  }
  if (form.failedLoginAttempts !== '') {
    const failed = Number(form.failedLoginAttempts);
    if (!Number.isInteger(failed) || failed < 0) errors.failedLoginAttempts = 'Cannot be negative';
    else if (failed > 1000) errors.failedLoginAttempts = 'Must be at most 1000';
  }
  if (form.totalLoginAttempts !== '') {
    const total = Number(form.totalLoginAttempts);
    if (!Number.isInteger(total) || total < 0) errors.totalLoginAttempts = 'Cannot be negative';
    else if (total > 1000) errors.totalLoginAttempts = 'Must be at most 1000';
  }
  if (form.failedLoginAttempts !== '' && form.totalLoginAttempts !== '') {
    if (Number(form.failedLoginAttempts) > Number(form.totalLoginAttempts)) {
      errors.failedLoginAttempts = 'Cannot exceed total login attempts';
    }
  }
  if (form.description && form.description.length > 1000) {
    errors.description = 'Must be at most 1000 characters';
  }
  return errors;
}

const PROTOCOLS = ['TCP', 'UDP', 'HTTP', 'HTTPS', 'SMTP', 'SMB', 'ICMP', 'FTP', 'SSH', 'RDP'];
const TRAFFIC_TYPES = ['Inbound', 'Outbound', 'Internal', 'Lateral'];
const USER_ROLES = ['Admin', 'Standard User', 'Service Account', 'Guest'];
const DEVICE_TYPES = ['Server', 'Workstation', 'Laptop', 'Mobile', 'IoT Device'];
const THREAT_CATEGORIES = ['Malware', 'DDoS', 'SQL Injection', 'XSS', 'Brute Force', 'Port Scan', 'Phishing', 'Ransomware', 'Insider Threat'];

// Representative feature values for each threat class, matching the AI service's
// training data distribution so a preset reliably produces a confident classification.
const PRESETS = [
  { key: 'MALWARE', label: 'Malware', sourceIp: '91.203.5.12', destinationIp: '10.0.1.20', port: 4444, protocol: 'TCP', packetSize: 50000, failedLoginAttempts: 0, totalLoginAttempts: 0, trafficType: 'Inbound', country: 'China', userRole: 'Standard User', deviceType: 'Workstation', threatCategory: 'Malware', description: 'Suspicious executable download over TCP.' },
  { key: 'DDOS', label: 'DDoS', sourceIp: '203.0.113.99', destinationIp: '10.0.0.1', port: 80, protocol: 'UDP', packetSize: 500000, failedLoginAttempts: 0, totalLoginAttempts: 0, trafficType: 'Inbound', country: 'Vietnam', userRole: 'Service Account', deviceType: 'Server', threatCategory: 'DDoS', description: 'High-volume UDP flood against web server.' },
  { key: 'SQL_INJECTION', label: 'SQL Injection', sourceIp: '45.33.32.10', destinationIp: '10.0.2.5', port: 443, protocol: 'HTTPS', packetSize: 8000, failedLoginAttempts: 0, totalLoginAttempts: 0, trafficType: 'Inbound', country: 'Brazil', userRole: 'Guest', deviceType: 'Server', threatCategory: 'SQL Injection', description: 'Malformed query parameters on login endpoint.' },
  { key: 'BRUTE_FORCE', label: 'Brute Force', sourceIp: '185.220.101.45', destinationIp: '10.0.0.5', port: 22, protocol: 'SSH', packetSize: 4800, failedLoginAttempts: 18, totalLoginAttempts: 20, trafficType: 'Inbound', country: 'Russia', userRole: 'Admin', deviceType: 'Server', threatCategory: 'Brute Force', description: 'Repeated SSH login failures from a single source.' },
  { key: 'PORT_SCAN', label: 'Port Scan', sourceIp: '185.220.101.45', destinationIp: '10.0.0.9', port: 8081, protocol: 'TCP', packetSize: 300, failedLoginAttempts: 0, totalLoginAttempts: 0, trafficType: 'Inbound', country: 'Netherlands', userRole: 'Guest', deviceType: 'Server', threatCategory: 'Port Scan', description: 'Sequential low-byte connection attempts across ports.' },
  { key: 'PHISHING', label: 'Phishing', sourceIp: '198.51.100.44', destinationIp: '10.0.4.7', port: 587, protocol: 'SMTP', packetSize: 12000, failedLoginAttempts: 0, totalLoginAttempts: 0, trafficType: 'Inbound', country: 'Nigeria', userRole: 'Standard User', deviceType: 'Workstation', threatCategory: 'Phishing', description: 'Suspicious email with credential-harvesting link.' },
  { key: 'RANSOMWARE', label: 'Ransomware', sourceIp: '198.51.100.23', destinationIp: '10.0.3.12', port: 445, protocol: 'SMB', packetSize: 900000, failedLoginAttempts: 0, totalLoginAttempts: 0, trafficType: 'Lateral', country: 'North Korea', userRole: 'Service Account', deviceType: 'Server', threatCategory: 'Ransomware', description: 'Mass file encryption activity over SMB shares.' },
  { key: 'INSIDER_THREAT', label: 'Insider Threat', sourceIp: '10.0.5.44', destinationIp: '10.0.6.2', port: 8443, protocol: 'HTTPS', packetSize: 150000, failedLoginAttempts: 2, totalLoginAttempts: 3, trafficType: 'Internal', country: 'United States', userRole: 'Admin', deviceType: 'Laptop', threatCategory: 'Insider Threat', description: 'Privileged account accessing unusual internal data volumes.' },
];

const emptyForm = {
  sourceIp: '', destinationIp: '', port: '', protocol: 'TCP', packetSize: '',
  failedLoginAttempts: '', totalLoginAttempts: '', trafficType: 'Inbound',
  country: '', userRole: 'Standard User', deviceType: 'Workstation', threatCategory: '', description: '',
};

const SORT_OPTIONS = [
  { value: 'createdAt', label: 'Run Time' },
  { value: 'sourceIp', label: 'Source IP' },
  { value: 'threatCategory', label: 'Category' },
];
const PAGE_SIZE_OPTIONS = [10, 20, 50];

export default function Simulate() {
  const [form, setForm] = useState(emptyForm);
  const [fieldErrors, setFieldErrors] = useState({});
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [result, setResult] = useState(null);

  const [history, setHistory] = useState([]);
  const [historyLoading, setHistoryLoading] = useState(true);
  const [filters, setFilters] = useState({ search: '', threatCategory: '' });
  const [historyPage, setHistoryPage] = useState(0);
  const [historyTotalPages, setHistoryTotalPages] = useState(0);
  const [historySize, setHistorySize] = useState(10);
  const [historySortBy, setHistorySortBy] = useState('createdAt');
  const [historySortDir, setHistorySortDir] = useState('desc');
  const [deleteTarget, setDeleteTarget] = useState(null);
  const [viewTarget, setViewTarget] = useState(null);

  const loadHistory = () => {
    setHistoryLoading(true);
    simulationService.search({
      page: historyPage,
      size: historySize,
      sort: `${historySortBy},${historySortDir}`,
      search: filters.search || undefined,
      threatCategory: filters.threatCategory || undefined,
    })
      .then(({ data }) => { setHistory(data.content); setHistoryTotalPages(data.totalPages); })
      .catch(() => {})
      .finally(() => setHistoryLoading(false));
  };

  useEffect(() => { loadHistory(); }, [historyPage, historySize, historySortBy, historySortDir]); // eslint-disable-line react-hooks/exhaustive-deps

  const applyPreset = (preset) => {
    const { key, label, ...fields } = preset;
    setForm({ ...emptyForm, ...fields });
    setFieldErrors({});
    setResult(null);
    setError('');
  };

  const handleChange = (field) => (e) => setForm({ ...form, [field]: e.target.value });

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    setResult(null);

    const errors = validateSimulationForm(form);
    setFieldErrors(errors);
    if (Object.keys(errors).length > 0) {
      setError('Please fix the highlighted fields before running the simulation.');
      return;
    }

    setLoading(true);
    try {
      const payload = {
        ...form,
        port: form.port === '' ? null : Number(form.port),
        packetSize: form.packetSize === '' ? 0 : Number(form.packetSize),
        failedLoginAttempts: form.failedLoginAttempts === '' ? 0 : Number(form.failedLoginAttempts),
        totalLoginAttempts: form.totalLoginAttempts === '' ? 0 : Number(form.totalLoginAttempts),
      };
      const { data } = await simulationService.run(payload);
      setResult(data);
      setHistoryPage(0);
      loadHistory();
    } catch (err) {
      setError(err.response?.data?.message || 'Simulation request failed.');
    } finally {
      setLoading(false);
    }
  };

  const handleSearchSubmit = (e) => {
    e.preventDefault();
    setHistoryPage(0);
    loadHistory();
  };

  const confirmDelete = async () => {
    await simulationService.remove(deleteTarget.id);
    setDeleteTarget(null);
    loadHistory();
  };

  return (
    <div className="space-y-6 max-w-4xl">
      <div>
        <h2 className="text-lg font-semibold text-slate-100 flex items-center gap-2">
          <FlaskConical size={20} className="text-cg-accent" /> Threat Simulation Lab
        </h2>
        <p className="text-sm text-slate-500 mt-1">
          Submit a synthetic security event to the live AI detection engine to see classification,
          explainability, MITRE mapping, and AI-recommended response actions in one place.
        </p>
      </div>

      <div className="cg-card">
        <p className="text-xs text-slate-400 uppercase mb-2">Quick Scenarios</p>
        <div className="flex flex-wrap gap-2">
          {PRESETS.map((p) => (
            <button
              key={p.key}
              type="button"
              onClick={() => applyPreset(p)}
              className="text-xs bg-cg-surface-alt border border-cg-border rounded-full px-3 py-1.5 text-slate-300 hover:text-cg-accent hover:border-cg-accent/40 transition"
            >
              {p.label}
            </button>
          ))}
        </div>
      </div>

      <form onSubmit={handleSubmit} className="cg-card space-y-4">
        {error && (
          <div className="bg-cg-danger/10 border border-cg-danger/30 text-cg-danger text-sm rounded-md px-3 py-2">
            {error}
          </div>
        )}

        <div className="grid grid-cols-2 gap-4">
          <div>
            <label className="block text-xs text-slate-400 mb-1">Source IP</label>
            <input
              required
              className={`cg-input w-full font-mono text-sm ${fieldErrors.sourceIp ? 'border-cg-danger' : ''}`}
              value={form.sourceIp}
              onChange={handleChange('sourceIp')}
              placeholder="185.220.101.45"
            />
            <FieldError message={fieldErrors.sourceIp} />
          </div>
          <div>
            <label className="block text-xs text-slate-400 mb-1">Destination IP</label>
            <input
              required
              className={`cg-input w-full font-mono text-sm ${fieldErrors.destinationIp ? 'border-cg-danger' : ''}`}
              value={form.destinationIp}
              onChange={handleChange('destinationIp')}
              placeholder="10.0.0.5"
            />
            <FieldError message={fieldErrors.destinationIp} />
          </div>
          <div>
            <label className="block text-xs text-slate-400 mb-1">Port</label>
            <input
              type="number"
              className={`cg-input w-full ${fieldErrors.port ? 'border-cg-danger' : ''}`}
              value={form.port}
              onChange={handleChange('port')}
              min={1}
              max={65535}
            />
            <FieldError message={fieldErrors.port} />
          </div>
          <div>
            <label className="block text-xs text-slate-400 mb-1">Protocol</label>
            <select className="cg-input w-full" value={form.protocol} onChange={handleChange('protocol')}>
              {PROTOCOLS.map((p) => <option key={p} value={p}>{p}</option>)}
            </select>
          </div>
          <div>
            <label className="block text-xs text-slate-400 mb-1">Packet Size (bytes)</label>
            <input
              type="number"
              className={`cg-input w-full ${fieldErrors.packetSize ? 'border-cg-danger' : ''}`}
              value={form.packetSize}
              onChange={handleChange('packetSize')}
              min={0}
            />
            <FieldError message={fieldErrors.packetSize} />
          </div>
          <div>
            <label className="block text-xs text-slate-400 mb-1">Failed Login Attempts</label>
            <input
              type="number"
              className={`cg-input w-full ${fieldErrors.failedLoginAttempts ? 'border-cg-danger' : ''}`}
              value={form.failedLoginAttempts}
              onChange={handleChange('failedLoginAttempts')}
              min={0}
              max={1000}
            />
            <FieldError message={fieldErrors.failedLoginAttempts} />
          </div>
          <div>
            <label className="block text-xs text-slate-400 mb-1">Total Login Attempts</label>
            <input
              type="number"
              className={`cg-input w-full ${fieldErrors.totalLoginAttempts ? 'border-cg-danger' : ''}`}
              value={form.totalLoginAttempts}
              onChange={handleChange('totalLoginAttempts')}
              min={0}
              max={1000}
            />
            <FieldError message={fieldErrors.totalLoginAttempts} />
          </div>
          <div>
            <label className="block text-xs text-slate-400 mb-1">Traffic Type</label>
            <select className="cg-input w-full" value={form.trafficType} onChange={handleChange('trafficType')}>
              {TRAFFIC_TYPES.map((t) => <option key={t} value={t}>{t}</option>)}
            </select>
          </div>
          <div>
            <label className="block text-xs text-slate-400 mb-1">Country</label>
            <input className="cg-input w-full" value={form.country} onChange={handleChange('country')} placeholder="e.g. Russia" />
          </div>
          <div>
            <label className="block text-xs text-slate-400 mb-1">User Role</label>
            <select className="cg-input w-full" value={form.userRole} onChange={handleChange('userRole')}>
              {USER_ROLES.map((r) => <option key={r} value={r}>{r}</option>)}
            </select>
          </div>
          <div>
            <label className="block text-xs text-slate-400 mb-1">Device Type</label>
            <select className="cg-input w-full" value={form.deviceType} onChange={handleChange('deviceType')}>
              {DEVICE_TYPES.map((d) => <option key={d} value={d}>{d}</option>)}
            </select>
          </div>
          <div>
            <label className="block text-xs text-slate-400 mb-1">Threat Category (hint)</label>
            <select className="cg-input w-full" value={form.threatCategory} onChange={handleChange('threatCategory')}>
              <option value="">Unspecified</option>
              {THREAT_CATEGORIES.map((c) => <option key={c} value={c}>{c}</option>)}
            </select>
          </div>
        </div>

        <div>
          <label className="block text-xs text-slate-400 mb-1">Description</label>
          <textarea
            className={`cg-input w-full ${fieldErrors.description ? 'border-cg-danger' : ''}`}
            rows={2}
            maxLength={1000}
            value={form.description}
            onChange={handleChange('description')}
            placeholder="Optional analyst notes about this simulated event..."
          />
          <FieldError message={fieldErrors.description} />
        </div>

        <button type="submit" disabled={loading} className="cg-btn-primary flex items-center justify-center gap-2 w-full">
          {loading && <Loader2 size={16} className="animate-spin" />}
          {loading ? 'Running AI detection...' : 'Run Simulation'}
        </button>
      </form>

      {result && (
        <div className="space-y-4">
          <div className="cg-card space-y-4 border-cg-accent/30">
            <div className="flex items-center justify-between flex-wrap gap-2">
              <div className="flex items-center gap-2">
                <ShieldCheck className="text-cg-accent" size={20} />
                <h3 className="text-base font-semibold text-slate-100">Prediction Result</h3>
              </div>
              <span className="text-xs text-slate-500 font-mono">Simulation #{result.simulationId}</span>
            </div>

            <div className="flex items-center gap-3 flex-wrap">
              <span className="text-lg font-semibold text-slate-100">{result.threat.threatType}</span>
              <SeverityBadge severity={result.threat.severity} />
              <StatusBadge status={result.threat.status} />
              <span className="text-sm text-slate-400">{Number(result.threat.confidenceScore).toFixed(1)}% confidence</span>
            </div>

            <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 text-xs">
              <Info label="Country" value={result.country || '—'} />
              <Info label="User Role" value={result.userRole || '—'} />
              <Info label="Device Type" value={result.deviceType || '—'} />
              <Info label="Traffic Type" value={result.trafficType || '—'} />
            </div>
            {result.description && (
              <p className="text-sm text-slate-400 bg-cg-surface-alt border border-cg-border rounded-md p-3">
                {result.description}
              </p>
            )}

            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 text-sm">
              <div className="bg-cg-surface-alt border border-cg-border rounded-md p-3">
                <p className="text-slate-200 font-medium">✓ Saved to threat database (#{result.threat.id})</p>
                <Link to={`/threats/${result.threat.id}`} className="text-cg-accent text-xs hover:underline inline-flex items-center gap-1 mt-1">
                  View threat detail <ExternalLink size={12} />
                </Link>
              </div>

              {result.incidentCreated ? (
                <div className="bg-cg-surface-alt border border-cg-border rounded-md p-3">
                  <p className="text-slate-200 font-medium">✓ Incident auto-created: {result.incidentNumber}</p>
                  <Link to={`/incidents/${result.incidentId}`} className="text-cg-accent text-xs hover:underline inline-flex items-center gap-1 mt-1">
                    View incident <ExternalLink size={12} />
                  </Link>
                </div>
              ) : (
                <div className="bg-cg-surface-alt border border-cg-border rounded-md p-3">
                  <p className="text-slate-400">No incident created — severity below the automated-response threshold.</p>
                </div>
              )}
            </div>

            <p className="text-xs text-slate-500">
              Prediction time: {new Date(result.predictionTime).toLocaleString()}
            </p>
          </div>

          <ExplainAiPanel explanation={result.explanation} />
        </div>
      )}

      <div className="cg-card space-y-4">
        <div className="flex items-center justify-between flex-wrap gap-2">
          <h3 className="text-sm font-semibold text-slate-300 flex items-center gap-2">
            <History size={16} className="text-cg-accent" /> Simulation History
          </h3>
          <button onClick={() => reportService.downloadSimulationsCsv()} className="cg-btn-secondary text-xs flex items-center gap-1.5">
            <Download size={13} /> Export CSV
          </button>
        </div>

        <form onSubmit={handleSearchSubmit} className="flex flex-wrap gap-3 items-end">
          <div className="flex-1 min-w-[180px]">
            <label className="block text-xs text-slate-400 mb-1">Search</label>
            <div className="relative">
              <Search size={14} className="absolute left-2.5 top-1/2 -translate-y-1/2 text-slate-500" />
              <input
                className="cg-input w-full pl-8"
                placeholder="Source/destination IP..."
                value={filters.search}
                onChange={(e) => setFilters({ ...filters, search: e.target.value })}
              />
            </div>
          </div>
          <div>
            <label className="block text-xs text-slate-400 mb-1">Category</label>
            <select className="cg-input" value={filters.threatCategory} onChange={(e) => setFilters({ ...filters, threatCategory: e.target.value })}>
              <option value="">All Categories</option>
              {THREAT_CATEGORIES.map((c) => <option key={c} value={c}>{c}</option>)}
            </select>
          </div>
          <button type="submit" className="cg-btn-secondary text-sm">Apply</button>
        </form>

        <div className="flex flex-wrap items-center gap-2 text-xs">
          <span className="text-slate-400">Sort by</span>
          <select className="cg-input text-xs" value={historySortBy} onChange={(e) => setHistorySortBy(e.target.value)}>
            {SORT_OPTIONS.map((o) => <option key={o.value} value={o.value}>{o.label}</option>)}
          </select>
          <select className="cg-input text-xs" value={historySortDir} onChange={(e) => setHistorySortDir(e.target.value)}>
            <option value="desc">Descending</option>
            <option value="asc">Ascending</option>
          </select>
          <span className="text-slate-400 ml-2">Rows per page</span>
          <select
            className="cg-input text-xs"
            value={historySize}
            onChange={(e) => { setHistorySize(Number(e.target.value)); setHistoryPage(0); }}
          >
            {PAGE_SIZE_OPTIONS.map((s) => <option key={s} value={s}>{s}</option>)}
          </select>
        </div>

        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="text-left text-slate-400 border-b border-cg-border">
                <th className="pb-2 pr-4">ID</th>
                <th className="pb-2 pr-4">Source → Destination</th>
                <th className="pb-2 pr-4">Category</th>
                <th className="pb-2 pr-4">Result</th>
                <th className="pb-2 pr-4">Run At</th>
                <th className="pb-2 pr-4">Actions</th>
              </tr>
            </thead>
            <tbody>
              {historyLoading ? (
                Array.from({ length: 5 }).map((_, i) => (
                  <tr key={i} className="border-b border-cg-border last:border-0">
                    <td className="py-2 pr-4" colSpan={6}><Skeleton className="h-4 w-full" /></td>
                  </tr>
                ))
              ) : history.length === 0 ? (
                <tr><td colSpan={6}><EmptyState title="No simulations yet" message="Run a scenario above to build simulation history." /></td></tr>
              ) : history.map((h) => (
                <tr key={h.id} className="border-b border-cg-border last:border-0">
                  <td className="py-2 pr-4 text-slate-500 font-mono text-xs">#{h.id}</td>
                  <td className="py-2 pr-4 font-mono text-xs text-slate-300">{h.sourceIp} → {h.destinationIp}</td>
                  <td className="py-2 pr-4 text-slate-400">{h.threatCategory || '—'}</td>
                  <td className="py-2 pr-4">
                    {h.resultThreat ? <SeverityBadge severity={h.resultThreat.severity} /> : <span className="text-slate-500 text-xs">—</span>}
                  </td>
                  <td className="py-2 pr-4 text-slate-500 text-xs">{new Date(h.createdAt).toLocaleString()}</td>
                  <td className="py-2 pr-4">
                    <div className="flex items-center gap-3 text-slate-400">
                      <button title="View Details" onClick={() => setViewTarget(h)} className="hover:text-cg-accent">
                        <Eye size={15} />
                      </button>
                      <button title="Delete" onClick={() => setDeleteTarget(h)} className="hover:text-cg-danger">
                        <Trash2 size={15} />
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>

        {historyTotalPages > 1 && (
          <div className="flex justify-center gap-2">
            <button disabled={historyPage === 0} onClick={() => setHistoryPage((p) => p - 1)} className="cg-btn-secondary disabled:opacity-40">Prev</button>
            <span className="text-sm text-slate-400 self-center">Page {historyPage + 1} of {historyTotalPages}</span>
            <button disabled={historyPage >= historyTotalPages - 1} onClick={() => setHistoryPage((p) => p + 1)} className="cg-btn-secondary disabled:opacity-40">Next</button>
          </div>
        )}
      </div>

      {viewTarget && (
        <Modal title={`Simulation #${viewTarget.id} Details`} onClose={() => setViewTarget(null)}>
          <div className="grid grid-cols-2 gap-3 text-sm">
            <Info label="Source IP" value={viewTarget.sourceIp} />
            <Info label="Destination IP" value={viewTarget.destinationIp} />
            <Info label="Port" value={viewTarget.port ?? '—'} />
            <Info label="Protocol" value={viewTarget.protocol || '—'} />
            <Info label="Packet Size" value={viewTarget.packetSize ?? '—'} />
            <Info label="Failed Logins" value={viewTarget.failedLoginAttempts ?? '—'} />
            <Info label="Total Login Attempts" value={viewTarget.totalLoginAttempts ?? '—'} />
            <Info label="Traffic Type" value={viewTarget.trafficType || '—'} />
            <Info label="Country" value={viewTarget.country || '—'} />
            <Info label="User Role" value={viewTarget.userRole || '—'} />
            <Info label="Device Type" value={viewTarget.deviceType || '—'} />
            <Info label="Threat Category" value={viewTarget.threatCategory || '—'} />
          </div>
          {viewTarget.description && (
            <p className="text-sm text-slate-400 bg-cg-surface-alt border border-cg-border rounded-md p-3 mt-3">
              {viewTarget.description}
            </p>
          )}
          {viewTarget.resultThreat && (
            <Link
              to={`/threats/${viewTarget.resultThreat.id}`}
              className="cg-btn-secondary text-sm mt-4 inline-flex items-center gap-2"
            >
              View resulting threat <ExternalLink size={13} />
            </Link>
          )}
        </Modal>
      )}

      {deleteTarget && (
        <Modal title="Delete Simulation" onClose={() => setDeleteTarget(null)} maxWidth="max-w-sm">
          <p className="text-sm text-slate-300 mb-4">
            Delete simulation <span className="font-semibold text-slate-100">#{deleteTarget.id}</span> from history?
            This only removes the simulation record — the resulting threat/incident data is not affected.
          </p>
          <div className="flex gap-3">
            <button onClick={() => setDeleteTarget(null)} className="cg-btn-secondary flex-1">Cancel</button>
            <button onClick={confirmDelete} className="bg-cg-danger text-white font-semibold px-4 py-2 rounded-md hover:bg-cg-danger/90 transition flex-1">
              Delete
            </button>
          </div>
        </Modal>
      )}
    </div>
  );
}

function Info({ label, value }) {
  return (
    <div>
      <p className="text-slate-500 uppercase text-[10px] tracking-wide">{label}</p>
      <p className="text-slate-200">{value}</p>
    </div>
  );
}

function FieldError({ message }) {
  if (!message) return null;
  return <p className="text-xs text-cg-danger mt-1">{message}</p>;
}
