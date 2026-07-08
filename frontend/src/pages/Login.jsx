import { useState } from 'react';
import { Link, useNavigate, useLocation } from 'react-router-dom';
import { ShieldCheck, Loader2, Crown, ShieldHalf, User, CheckCircle2 } from 'lucide-react';
import { useAuth } from '../context/AuthContext';

// Demo Account Selector — for academic demonstration only. Safe to delete this
// constant and the "Demo Accounts" block below before a production deployment.
const DEMO_ACCOUNTS = [
  { label: 'Administrator', username: 'admin', password: 'Password@123', icon: Crown, accent: 'text-cg-warning' },
  { label: 'Security Analyst', username: 'alice.analyst', password: 'Password@123', icon: ShieldHalf, accent: 'text-cg-info' },
  { label: 'Standard User', username: 'bob.user', password: 'Password@123', icon: User, accent: 'text-cg-accent' },
];

export default function Login() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [form, setForm] = useState({ usernameOrEmail: '', password: '' });
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const [justRegistered, setJustRegistered] = useState(!!location.state?.registered);

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    setLoading(true);
    try {
      const data = await login(form.usernameOrEmail, form.password);
      navigate(data.mustChangePassword ? '/force-password-change' : '/dashboard');
    } catch (err) {
      setError(err.response?.data?.message || 'Invalid username or password');
    } finally {
      setLoading(false);
    }
  };

  const useDemoAccount = (account) => {
    setError('');
    setForm({ usernameOrEmail: account.username, password: account.password });
  };

  return (
    <div className="min-h-screen bg-cg-bg flex items-center justify-center px-4 py-10">
      <div className="w-full max-w-md">
        <div className="flex flex-col items-center mb-8">
          <ShieldCheck className="text-cg-accent mb-2" size={40} />
          <h1 className="text-xl font-bold text-slate-100">CyberGuard Platform</h1>
          <p className="text-sm text-slate-500">AI-Based Threat Detection &amp; Incident Response</p>
        </div>

        <form onSubmit={handleSubmit} className="cg-card space-y-4">
          <h2 className="text-lg font-semibold text-slate-100 mb-2">Sign In</h2>

          {justRegistered && (
            <div className="bg-cg-success/10 border border-cg-success/30 text-cg-success text-sm rounded-md px-3 py-2 flex items-center gap-2">
              <CheckCircle2 size={16} />
              Registration successful! Please sign in with your new account.
            </div>
          )}

          {error && (
            <div className="bg-cg-danger/10 border border-cg-danger/30 text-cg-danger text-sm rounded-md px-3 py-2">
              {error}
            </div>
          )}

          <div>
            <label className="block text-xs text-slate-400 mb-1">Username or Email</label>
            <input
              type="text"
              required
              className="cg-input w-full"
              value={form.usernameOrEmail}
              onChange={(e) => setForm({ ...form, usernameOrEmail: e.target.value })}
              placeholder="admin"
            />
          </div>

          <div>
            <label className="block text-xs text-slate-400 mb-1">Password</label>
            <input
              type="password"
              required
              className="cg-input w-full"
              value={form.password}
              onChange={(e) => setForm({ ...form, password: e.target.value })}
              placeholder="••••••••"
            />
          </div>

          <button type="submit" disabled={loading} className="cg-btn-primary w-full flex items-center justify-center gap-2">
            {loading && <Loader2 size={16} className="animate-spin" />}
            {loading ? 'Signing in...' : 'Sign In'}
          </button>

          <p className="text-sm text-slate-500 text-center">
            Don&apos;t have an account?{' '}
            <Link to="/register" className="text-cg-accent hover:underline">
              Register
            </Link>
          </p>
        </form>

        <div className="mt-6">
          <p className="text-xs text-slate-500 text-center mb-3">
            Demo Accounts <span className="text-slate-600">(For Academic Demonstration Only)</span>
          </p>
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
            {DEMO_ACCOUNTS.map((account) => {
              const Icon = account.icon;
              return (
                <div key={account.username} className="cg-card !p-3 flex flex-col items-center text-center gap-1.5">
                  <Icon size={20} className={account.accent} />
                  <p className="text-xs font-semibold text-slate-200">{account.label}</p>
                  <p className="text-[11px] text-slate-500 leading-tight break-all">{account.username}</p>
                  <button
                    type="button"
                    onClick={() => useDemoAccount(account)}
                    className="cg-btn-secondary w-full text-xs mt-1 py-1.5"
                  >
                    Use Demo Account
                  </button>
                </div>
              );
            })}
          </div>
        </div>
      </div>
    </div>
  );
}
