import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { ShieldCheck, Loader2 } from 'lucide-react';
import { authService } from '../services/authService';

const initialForm = {
  fullName: '', username: '', email: '', password: '', department: '', phone: '',
};

export default function Register() {
  const navigate = useNavigate();
  const [form, setForm] = useState(initialForm);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  const handleChange = (field) => (e) => setForm({ ...form, [field]: e.target.value });

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    setLoading(true);
    try {
      // New accounts always go through the existing register + JWT flow, but the tokens are
      // intentionally discarded here - every registration is assigned the standard USER role
      // server-side, and the user must sign in explicitly rather than being auto-logged-in.
      await authService.register(form);
      navigate('/login', { state: { registered: true } });
    } catch (err) {
      setError(err.response?.data?.message || 'Registration failed. Please check your details.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen bg-cg-bg flex items-center justify-center px-4 py-10">
      <div className="w-full max-w-md">
        <div className="flex flex-col items-center mb-6">
          <ShieldCheck className="text-cg-accent mb-2" size={36} />
          <h1 className="text-xl font-bold text-slate-100">Create Account</h1>
        </div>

        <form onSubmit={handleSubmit} className="cg-card space-y-3">
          {error && (
            <div className="bg-cg-danger/10 border border-cg-danger/30 text-cg-danger text-sm rounded-md px-3 py-2">
              {error}
            </div>
          )}

          <div>
            <label className="block text-xs text-slate-400 mb-1">Full Name</label>
            <input required maxLength={120} className="cg-input w-full" value={form.fullName} onChange={handleChange('fullName')} />
          </div>

          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="block text-xs text-slate-400 mb-1">Username</label>
              <input required minLength={3} maxLength={60} className="cg-input w-full" value={form.username} onChange={handleChange('username')} />
            </div>
            <div>
              <label className="block text-xs text-slate-400 mb-1">Email</label>
              <input required type="email" maxLength={150} className="cg-input w-full" value={form.email} onChange={handleChange('email')} />
            </div>
          </div>

          <div>
            <label className="block text-xs text-slate-400 mb-1">Password</label>
            <input required type="password" minLength={8} maxLength={100} className="cg-input w-full" value={form.password} onChange={handleChange('password')} />
          </div>

          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="block text-xs text-slate-400 mb-1">Department</label>
              <input maxLength={100} className="cg-input w-full" value={form.department} onChange={handleChange('department')} />
            </div>
            <div>
              <label className="block text-xs text-slate-400 mb-1">Phone</label>
              <input maxLength={30} className="cg-input w-full" value={form.phone} onChange={handleChange('phone')} />
            </div>
          </div>

          <button type="submit" disabled={loading} className="cg-btn-primary w-full flex items-center justify-center gap-2 mt-2">
            {loading && <Loader2 size={16} className="animate-spin" />}
            {loading ? 'Creating account...' : 'Register'}
          </button>

          <p className="text-sm text-slate-500 text-center">
            Already have an account?{' '}
            <Link to="/login" className="text-cg-accent hover:underline">
              Sign In
            </Link>
          </p>
        </form>
      </div>
    </div>
  );
}
