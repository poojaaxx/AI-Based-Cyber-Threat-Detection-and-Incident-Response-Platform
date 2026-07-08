import { useEffect, useState } from 'react';
import { BookOpen, Radar, Crosshair } from 'lucide-react';
import { SeverityBadge } from '../components/common/Badge';
import { Skeleton } from '../components/common/Skeleton';
import EmptyState from '../components/common/EmptyState';
import { threatIntelService } from '../services/threatIntelService';

const TABS = ['CVE Database', 'IOC Feed', 'MITRE ATT&CK'];

export default function ThreatIntel() {
  const [tab, setTab] = useState(0);
  const [cves, setCves] = useState([]);
  const [iocs, setIocs] = useState([]);
  const [techniques, setTechniques] = useState([]);
  const [query, setQuery] = useState('');
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    setLoading(true);
    if (tab === 0) threatIntelService.searchCves(query).then(({ data }) => setCves(data.content)).finally(() => setLoading(false));
    if (tab === 1) threatIntelService.getIocs().then(({ data }) => setIocs(data.content)).finally(() => setLoading(false));
    if (tab === 2) threatIntelService.getMitreTechniques().then(({ data }) => setTechniques(data)).finally(() => setLoading(false));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [tab, query]);

  return (
    <div className="space-y-4">
      <div>
        <h2 className="text-lg font-semibold text-slate-100 flex items-center gap-2">
          <BookOpen size={20} className="text-cg-accent" /> Threat Intelligence
        </h2>
        <p className="text-sm text-slate-500 mt-1">CVE records, indicators of compromise, and MITRE ATT&amp;CK technique references.</p>
      </div>

      <div className="flex gap-2 border-b border-cg-border">
        {TABS.map((t, idx) => (
          <button
            key={t}
            onClick={() => setTab(idx)}
            className={`px-4 py-2 text-sm font-medium border-b-2 transition ${
              tab === idx ? 'border-cg-accent text-cg-accent' : 'border-transparent text-slate-400 hover:text-slate-200'
            }`}
          >
            {t}
          </button>
        ))}
      </div>

      {tab === 0 && (
        <div className="space-y-3">
          <input
            className="cg-input w-full max-w-sm"
            placeholder="Search by CVE ID or title..."
            value={query}
            onChange={(e) => setQuery(e.target.value)}
          />
          <div className="grid gap-3">
            {loading ? (
              Array.from({ length: 3 }).map((_, i) => <Skeleton key={i} className="h-24 w-full rounded-xl" />)
            ) : cves.length === 0 ? (
              <EmptyState icon={BookOpen} title="No CVE records found" message="Try a different search term." />
            ) : cves.map((c) => (
              <div key={c.id} className="cg-card cg-card-hover">
                <div className="flex items-center justify-between mb-1">
                  <p className="font-mono text-cg-accent text-sm">{c.cveId}</p>
                  <div className="flex items-center gap-2">
                    <span className="text-xs text-slate-500">CVSS {c.cvssScore}</span>
                    <SeverityBadge severity={c.severity} />
                  </div>
                </div>
                <p className="text-sm font-medium text-slate-200">{c.title}</p>
                <p className="text-xs text-slate-500 mt-1">{c.description}</p>
                <p className="text-xs text-slate-600 mt-1">Affected: {c.affectedProducts}</p>
              </div>
            ))}
          </div>
        </div>
      )}

      {tab === 1 && (
        <div className="cg-card overflow-x-auto !p-0">
          <table className="w-full text-sm">
            <thead>
              <tr className="text-left text-slate-400 border-b border-cg-border">
                <th className="py-3 pl-5 pr-4">Type</th>
                <th className="py-3 pr-4">Value</th>
                <th className="py-3 pr-4">Threat</th>
                <th className="py-3 pr-4">Confidence</th>
                <th className="py-3 pr-4">Source</th>
              </tr>
            </thead>
            <tbody>
              {loading ? (
                Array.from({ length: 5 }).map((_, i) => (
                  <tr key={i} className="border-b border-cg-border last:border-0">
                    <td className="py-2.5 pl-5 pr-4" colSpan={5}><Skeleton className="h-4 w-full" /></td>
                  </tr>
                ))
              ) : iocs.length === 0 ? (
                <tr><td colSpan={5}><EmptyState icon={Radar} title="No active IOCs" message="Indicators of compromise will appear here as they're identified." /></td></tr>
              ) : iocs.map((i) => (
                <tr key={i.id} className="cg-table-row">
                  <td className="py-2.5 pl-5 pr-4 text-slate-300">{i.iocType}</td>
                  <td className="py-2.5 pr-4 font-mono text-xs text-slate-300">{i.iocValue}</td>
                  <td className="py-2.5 pr-4 text-slate-400">{i.threatType}</td>
                  <td className="py-2.5 pr-4 text-slate-400">{i.confidenceScore}%</td>
                  <td className="py-2.5 pr-4 text-slate-500 text-xs">{i.source}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {tab === 2 && (
        <div className="grid gap-3">
          {loading ? (
            Array.from({ length: 3 }).map((_, i) => <Skeleton key={i} className="h-20 w-full rounded-xl" />)
          ) : techniques.length === 0 ? (
            <EmptyState icon={Crosshair} title="No MITRE techniques loaded" message="Technique mappings will appear here once available." />
          ) : techniques.map((t) => (
            <div key={t.id} className="cg-card cg-card-hover">
              <div className="flex items-center gap-2 mb-1">
                <span className="text-cg-accent font-mono text-sm">{t.techniqueId}</span>
                <span className="text-xs text-slate-500 bg-cg-surface-alt px-2 py-0.5 rounded">{t.tactic}</span>
              </div>
              <p className="text-sm font-medium text-slate-200">{t.name}</p>
              <p className="text-xs text-slate-500 mt-1">{t.description}</p>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
