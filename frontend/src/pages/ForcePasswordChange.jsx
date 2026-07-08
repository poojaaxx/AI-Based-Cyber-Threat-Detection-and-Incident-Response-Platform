import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { ShieldAlert, Loader2 } from 'lucide-react';
import { settingsService } from '../services/settingsService';
import { useAuth } from '../context/AuthContext';

export default function ForcePasswordChange() {
  const { clearMustChangePassword, logout } = useAuth();
  const navigate = useNavigate();
  const [form, setForm] = useState({ currentPassword: '', newPassword: '', confirmPassword: '' });
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    if (form.newPassword !== form.confirmPassword) {
      setError('New password and confirmation do not match');
      return;
    }
    setLoading(true);
    try {
      await settingsService.changePassword({
        currentPassword: form.currentPassword,
        newPassword: form.newPassword,
      });
      clearMustChangePassword();
      navigate('/dashboard');
    } catch (err) {
      setError(err.response?.data?.message || 'Failed to change password.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen bg-cg-bg flex items-center justify-center px-4">
      <div className="w-full max-w-md">
        <div className="flex flex-col items-center mb-8">
          <ShieldAlert className="text-cg-warning mb-2" size={40} />
          <h1 className="text-xl font-bold text-slate-100">Password Change Required</h1>
          <p className="text-sm text-slate-500 text-center mt-1">
            Your password was reset by an administrator. Set a new password to continue.
          </p>
        </div>

        <form onSubmit={handleSubmit} className="cg-card space-y-4">
          {error && (
            <div className="bg-cg-danger/10 border border-cg-danger/30 text-cg-danger text-sm rounded-md px-3 py-2">
              {error}
            </div>
          )}

          <div>
            <label className="block text-xs text-slate-400 mb-1">Temporary Password</label>
            <input
              type="password"
              required
              className="cg-input w-full"
              value={form.currentPassword}
              onChange={(e) => setForm({ ...form, currentPassword: e.target.value })}
            />
          </div>

          <div>
            <label className="block text-xs text-slate-400 mb-1">New Password</label>
            <input
              type="password"
              required
              minLength={8}
              className="cg-input w-full"
              value={form.newPassword}
              onChange={(e) => setForm({ ...form, newPassword: e.target.value })}
            />
          </div>

          <div>
            <label className="block text-xs text-slate-400 mb-1">Confirm New Password</label>
            <input
              type="password"
              required
              minLength={8}
              className="cg-input w-full"
              value={form.confirmPassword}
              onChange={(e) => setForm({ ...form, confirmPassword: e.target.value })}
            />
          </div>

          <button type="submit" disabled={loading} className="cg-btn-primary w-full flex items-center justify-center gap-2">
            {loading && <Loader2 size={16} className="animate-spin" />}
            {loading ? 'Updating...' : 'Set New Password'}
          </button>

          <button type="button" onClick={logout} className="text-sm text-slate-500 hover:text-slate-300 w-full text-center">
            Sign out instead
          </button>
        </form>
      </div>
    </div>
  );
}
