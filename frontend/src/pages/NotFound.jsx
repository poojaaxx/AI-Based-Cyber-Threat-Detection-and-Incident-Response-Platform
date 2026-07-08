import { Link } from 'react-router-dom';
import { ShieldAlert } from 'lucide-react';

export default function NotFound() {
  return (
    <div className="min-h-screen bg-cg-bg flex flex-col items-center justify-center text-center px-4 animate-fade-in">
      <div className="w-20 h-20 rounded-full bg-cg-surface border border-cg-border flex items-center justify-center mb-5 shadow-md shadow-black/20">
        <ShieldAlert className="text-cg-accent" size={36} />
      </div>
      <h1 className="text-2xl font-bold text-slate-100">404 - Page Not Found</h1>
      <p className="text-slate-500 mt-2 mb-6 max-w-xs">The page you're looking for doesn't exist or may have been moved.</p>
      <Link to="/dashboard" className="cg-btn-primary">Back to Dashboard</Link>
    </div>
  );
}
