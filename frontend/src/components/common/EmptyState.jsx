import { Link } from 'react-router-dom';
import { Inbox } from 'lucide-react';

/** Standard empty-state block: icon, message, optional call-to-action. Reused across every table/list/page. */
export default function EmptyState({ icon: Icon = Inbox, title = 'Nothing here yet', message, actionLabel, onAction, actionTo }) {
  return (
    <div className="flex flex-col items-center justify-center text-center py-12 px-4">
      <div className="w-14 h-14 rounded-full bg-cg-surface-alt border border-cg-border flex items-center justify-center mb-4">
        <Icon size={24} className="text-slate-500" />
      </div>
      <p className="text-sm font-medium text-slate-300">{title}</p>
      {message && <p className="text-xs text-slate-500 mt-1 max-w-xs">{message}</p>}
      {actionLabel && (onAction || actionTo) && (
        actionTo ? (
          <Link to={actionTo} className="cg-btn-secondary text-xs mt-4">{actionLabel}</Link>
        ) : (
          <button onClick={onAction} className="cg-btn-secondary text-xs mt-4">{actionLabel}</button>
        )
      )}
    </div>
  );
}
