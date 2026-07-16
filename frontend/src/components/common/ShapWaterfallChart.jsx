import { useState } from 'react';

// Diverging pair for SHAP contribution direction, reusing the app's own
// palette tokens (tailwind.config.js: cg-accent-dim, cg-danger) rather than
// inventing new colors. Validated with the dataviz skill's palette checker
// against the app's dark card surface (#111827): lightness band, CVD
// separation (deltaE 20.2 deutan / 45.0 tritan), normal-vision floor (39.7),
// and contrast all pass.
const POSITIVE_COLOR = '#0a8f7f'; // cg-accent-dim - "toward predicted class"
const NEGATIVE_COLOR = '#ef4444'; // cg-danger - "away from predicted class"

/**
 * A SHAP waterfall chart: shows the model's base rate for the predicted class,
 * then each feature's contribution as a floating bar walking the cumulative
 * total up/down, ending at the actual predicted probability. Unlike a plain
 * ranked list, the steps are mathematically honest - base + every factor
 * (including the bundled "N other features" bar) sums exactly to the final
 * predicted probability; nothing is silently dropped by only showing the top few.
 */
export default function ShapWaterfallChart({ baseValue, factors, otherContribution, otherFeatureCount, finalValue }) {
  const [hovered, setHovered] = useState(null);
  const [showTable, setShowTable] = useState(false);

  const steps = [];
  let cumulative = baseValue;
  for (const f of factors || []) {
    const cumBefore = cumulative;
    cumulative += f.importance;
    steps.push({
      label: f.feature, delta: f.importance, description: f.description, value: f.value,
      cumBefore, cumAfter: cumulative,
    });
  }
  if (otherFeatureCount > 0) {
    const cumBefore = cumulative;
    cumulative += otherContribution;
    steps.push({
      label: `${otherFeatureCount} other feature${otherFeatureCount === 1 ? '' : 's'}`,
      delta: otherContribution,
      description: `Combined SHAP contribution of the ${otherFeatureCount} lower-ranked features not shown individually above.`,
      value: null,
      cumBefore, cumAfter: cumulative,
    });
  }

  const pct = (v) => `${(Math.max(0, Math.min(1, v)) * 100).toFixed(2)}%`;

  return (
    <div className="space-y-2">
      <div className="flex items-center justify-between text-xs text-slate-400">
        <div className="flex items-center gap-4">
          <span className="flex items-center gap-1.5">
            <span className="inline-block w-3 h-2 rounded-sm" style={{ background: POSITIVE_COLOR }} />
            Toward predicted class
          </span>
          <span className="flex items-center gap-1.5">
            <span className="inline-block w-3 h-2 rounded-sm" style={{ background: NEGATIVE_COLOR }} />
            Away from predicted class
          </span>
        </div>
        <button onClick={() => setShowTable((v) => !v)} className="text-cg-accent hover:underline">
          {showTable ? 'View as chart' : 'View as table'}
        </button>
      </div>

      {showTable ? (
        <table className="w-full text-xs">
          <thead>
            <tr className="text-slate-500 uppercase text-left">
              <th className="py-1 pr-2">Step</th>
              <th className="py-1 pr-2">Value</th>
              <th className="py-1 text-right">Contribution</th>
            </tr>
          </thead>
          <tbody>
            <tr className="border-t border-cg-border">
              <td className="py-1 pr-2 text-slate-300">Base value</td>
              <td className="py-1 pr-2 text-slate-500">-</td>
              <td className="py-1 text-right font-mono text-slate-300">{pct(baseValue)}</td>
            </tr>
            {steps.map((s, i) => (
              <tr key={i} className="border-t border-cg-border">
                <td className="py-1 pr-2 text-slate-300">{s.label}</td>
                <td className="py-1 pr-2 text-slate-500">{s.value ?? '-'}</td>
                <td className="py-1 text-right font-mono" style={{ color: s.delta >= 0 ? POSITIVE_COLOR : NEGATIVE_COLOR }}>
                  {s.delta >= 0 ? '+' : ''}{pct(s.delta)}
                </td>
              </tr>
            ))}
            <tr className="border-t border-cg-border">
              <td className="py-1 pr-2 text-slate-200 font-semibold">Predicted probability</td>
              <td className="py-1 pr-2 text-slate-500">-</td>
              <td className="py-1 text-right font-mono text-slate-200 font-semibold">{pct(finalValue)}</td>
            </tr>
          </tbody>
        </table>
      ) : (
        <div>
          <WaterfallRow label="Base value" position={baseValue} isReference />
          {steps.map((s, i) => (
            <WaterfallRow
              key={i}
              label={s.label}
              cumBefore={s.cumBefore}
              cumAfter={s.cumAfter}
              delta={s.delta}
              isHovered={hovered === i}
              onHover={() => setHovered(i)}
              onLeave={() => setHovered(null)}
              tooltip={s.description}
            />
          ))}
          <WaterfallRow label="Predicted probability" position={finalValue} isReference isFinal />
        </div>
      )}
    </div>
  );
}

function WaterfallRow({ label, position, cumBefore, cumAfter, delta, isReference, isFinal, isHovered, onHover, onLeave, tooltip }) {
  const pct = (v) => `${Math.max(0, Math.min(1, v)) * 100}%`;
  const isPositive = delta >= 0;
  const barLeft = isReference ? 0 : Math.min(cumBefore, cumAfter);
  const barWidth = isReference ? 0 : Math.max(Math.abs(cumAfter - cumBefore), 0.006);

  return (
    <div className="flex items-center gap-2 py-1" style={{ marginBottom: 2 }}>
      <div className="w-36 shrink-0 text-xs text-slate-400 truncate" title={label}>
        {label}
      </div>
      <div className="relative flex-1 h-5 rounded bg-cg-surface-alt/40">
        {isReference ? (
          <div
            className="absolute top-0 bottom-0 w-0.5"
            style={{ left: pct(position), background: isFinal ? '#94a3b8' : '#64748b' }}
          />
        ) : (
          <div
            role="img"
            aria-label={tooltip}
            tabIndex={0}
            onMouseEnter={onHover}
            onMouseLeave={onLeave}
            onFocus={onHover}
            onBlur={onLeave}
            className="absolute top-0.5 bottom-0.5 rounded outline-none"
            style={{
              left: pct(barLeft),
              width: pct(barWidth),
              background: isPositive ? '#0a8f7f' : '#ef4444',
              boxShadow: isHovered ? '0 0 0 2px rgba(255,255,255,0.25)' : 'none',
            }}
          />
        )}
        {isHovered && tooltip && (
          <div className="absolute z-10 left-0 -top-9 bg-cg-bg border border-cg-border rounded-md px-2 py-1 text-xs text-slate-200 shadow-lg max-w-xs">
            {tooltip}
          </div>
        )}
      </div>
      <div className="w-16 shrink-0 text-xs font-mono text-right text-slate-300">
        {isReference ? `${(position * 100).toFixed(1)}%` : `${isPositive ? '+' : ''}${(delta * 100).toFixed(1)}%`}
      </div>
    </div>
  );
}
