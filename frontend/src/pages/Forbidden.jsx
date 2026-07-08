import { Link } from 'react-router-dom';
import { ShieldOff } from 'lucide-react';

export default function Forbidden() {
  return (
    <div className="min-h-screen bg-cg-bg flex flex-col items-center justify-center text-center px-4">
      <ShieldOff className="text-cg-danger mb-4" size={48} />
      <h1 className="text-2xl font-bold text-slate-100">403 - Access Denied</h1>
      <p className="text-slate-500 mt-2 mb-6">
        Your account role does not have permission to view this page.
      </p>
      <Link to="/dashboard" className="cg-btn-primary">Back to Dashboard</Link>
    </div>
  );
}
