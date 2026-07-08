/** Generic pulse-animated placeholder block, sized via className (e.g. "h-64 w-full"). */
export function Skeleton({ className = 'h-4 w-full' }) {
  return <div className={`animate-pulse rounded-md bg-cg-surface-alt ${className}`} />;
}

/** A cg-card-shaped skeleton for chart/widget placeholders while a page's data is loading. */
export function ChartSkeleton({ height = 260, title = true }) {
  return (
    <div className="cg-card">
      {title && <Skeleton className="h-4 w-40 mb-4" />}
      <div style={{ height }} className="w-full animate-pulse rounded-md bg-cg-surface-alt" />
    </div>
  );
}
