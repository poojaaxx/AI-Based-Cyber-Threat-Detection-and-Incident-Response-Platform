import { RISK_LEVEL_HEX } from '../../utils/chartColors';

const LEVEL_LABEL = { LOW: 'Low', MEDIUM: 'Medium', HIGH: 'High', CRITICAL: 'Critical' };

/**
 * Professional animated semicircle risk gauge. Pure SVG (no charting library) so the
 * arc-fill and color can both transition smoothly via CSS when the score prop changes.
 */
export default function RiskGauge({ score = 0, level = 'LOW', lastUpdated, size = 220 }) {
  const radius = 80;
  const cx = 100;
  const cy = 100;
  const circumference = Math.PI * radius;
  const clamped = Math.max(0, Math.min(100, score));
  const offset = circumference * (1 - clamped / 100);
  const color = RISK_LEVEL_HEX[level] || RISK_LEVEL_HEX.LOW;

  return (
    <div className="flex flex-col items-center" style={{ width: size }}>
      <svg viewBox="0 0 200 120" width={size} height={size * 0.6}>
        <path
          d={`M ${cx - radius},${cy} A ${radius},${radius} 0 1 1 ${cx + radius},${cy}`}
          fill="none"
          stroke="#1f2937"
          strokeWidth={16}
          strokeLinecap="round"
        />
        <path
          d={`M ${cx - radius},${cy} A ${radius},${radius} 0 1 1 ${cx + radius},${cy}`}
          fill="none"
          stroke={color}
          strokeWidth={16}
          strokeLinecap="round"
          strokeDasharray={circumference}
          strokeDashoffset={offset}
          style={{ transition: 'stroke-dashoffset 1s ease-out, stroke 0.6s ease-out' }}
        />
      </svg>

      <div className="-mt-14 text-center">
        <p className="text-4xl font-bold transition-colors duration-500" style={{ color }}>
          {Math.round(clamped)}
        </p>
        <p className="text-xs text-slate-500">out of 100</p>
        <p className="text-sm font-semibold mt-1 transition-colors duration-500" style={{ color }}>
          {LEVEL_LABEL[level] || level}
        </p>
      </div>

      {lastUpdated && (
        <p className="text-[10px] text-slate-600 mt-3">
          Last updated {new Date(lastUpdated).toLocaleTimeString()}
        </p>
      )}
    </div>
  );
}
