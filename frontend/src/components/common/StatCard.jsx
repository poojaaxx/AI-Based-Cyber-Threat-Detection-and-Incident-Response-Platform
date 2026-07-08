import { useCountUp } from '../../hooks/useCountUp';

export default function StatCard({ label, value, icon: Icon, accent = 'text-cg-accent', hint, topAccent }) {
  const displayValue = useCountUp(value);

  return (
    <div className={`cg-card cg-card-hover flex items-start justify-between ${topAccent ? `cg-card-top-${topAccent}` : ''}`}>
      <div className="min-w-0">
        <p className="text-xs uppercase tracking-wide text-slate-400 mb-1">{label}</p>
        <p className={`text-2xl font-bold truncate tabular-nums ${accent}`} title={typeof value === 'string' ? value : undefined}>{displayValue}</p>
        {hint && <p className="text-xs text-slate-500 mt-1">{hint}</p>}
      </div>
      {Icon && (
        <div className={`p-2 rounded-lg bg-cg-surface-alt ${accent}`}>
          <Icon size={22} />
        </div>
      )}
    </div>
  );
}
