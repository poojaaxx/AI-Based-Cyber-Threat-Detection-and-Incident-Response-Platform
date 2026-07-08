import { useEffect, useState } from 'react';
import { RefreshCw, Settings as SettingsIcon, UserCircle2, KeyRound, Bell, Webhook } from 'lucide-react';
import { settingsService } from '../services/settingsService';
import { useAuth } from '../context/AuthContext';

export default function Settings() {
  const { user } = useAuth();
  const [profile, setProfile] = useState({ fullName: '', department: '', phone: '' });
  const [password, setPassword] = useState({ currentPassword: '', newPassword: '' });
  const [prefs, setPrefs] = useState(null);
  const [apiConfig, setApiConfig] = useState(null);
  const [message, setMessage] = useState('');
  const [messageType, setMessageType] = useState('success');

  useEffect(() => {
    settingsService.getCurrentUser().then(({ data }) => setProfile({ fullName: data.fullName, department: data.department || '', phone: data.phone || '' }));
    settingsService.getNotificationPreferences().then(({ data }) => setPrefs(data));
    settingsService.getApiConfiguration().then(({ data }) => setApiConfig(data)).catch(() => setApiConfig(null));
  }, []);

  const flash = (msg, type = 'success') => {
    setMessage(msg);
    setMessageType(type);
    setTimeout(() => setMessage(''), 3000);
  };

  const handleProfileSubmit = async (e) => {
    e.preventDefault();
    await settingsService.updateProfile(profile);
    flash('Profile updated successfully.');
  };

  const handlePasswordSubmit = async (e) => {
    e.preventDefault();
    try {
      await settingsService.changePassword(password);
      setPassword({ currentPassword: '', newPassword: '' });
      flash('Password changed successfully.');
    } catch (err) {
      flash(err.response?.data?.message || 'Failed to change password.', 'error');
    }
  };

  const handlePrefsChange = async (field) => {
    const updated = { ...prefs, [field]: !prefs[field] };
    setPrefs(updated);
    await settingsService.updateNotificationPreferences(updated);
  };

  const handleRegenerateKey = async () => {
    const { data } = await settingsService.regenerateApiKey();
    setApiConfig(data);
    flash('API key regenerated.');
  };

  return (
    <div className="space-y-6 max-w-2xl">
      <div>
        <h2 className="text-lg font-semibold text-slate-100 flex items-center gap-2">
          <SettingsIcon size={20} className="text-cg-accent" /> Settings
        </h2>
        <p className="text-sm text-slate-500 mt-1">Manage your profile, security, notifications, and API access.</p>
      </div>

      {message && (
        <div className={`text-sm rounded-md px-3 py-2 border animate-fade-in ${
          messageType === 'error'
            ? 'bg-cg-danger/10 border-cg-danger/30 text-cg-danger'
            : 'bg-cg-success/10 border-cg-success/30 text-cg-success'
        }`}>
          {message}
        </div>
      )}

      <form onSubmit={handleProfileSubmit} className="cg-card space-y-3">
        <h3 className="text-sm font-semibold text-slate-300 flex items-center gap-2">
          <UserCircle2 size={15} className="text-cg-accent" /> Profile ({user?.username})
        </h3>
        <div>
          <label className="block text-xs text-slate-400 mb-1">Full Name</label>
          <input className="cg-input w-full" value={profile.fullName} onChange={(e) => setProfile({ ...profile, fullName: e.target.value })} />
        </div>
        <div className="grid grid-cols-2 gap-3">
          <div>
            <label className="block text-xs text-slate-400 mb-1">Department</label>
            <input className="cg-input w-full" value={profile.department} onChange={(e) => setProfile({ ...profile, department: e.target.value })} />
          </div>
          <div>
            <label className="block text-xs text-slate-400 mb-1">Phone</label>
            <input className="cg-input w-full" value={profile.phone} onChange={(e) => setProfile({ ...profile, phone: e.target.value })} />
          </div>
        </div>
        <button type="submit" className="cg-btn-primary">Save Profile</button>
      </form>

      <form onSubmit={handlePasswordSubmit} className="cg-card space-y-3">
        <h3 className="text-sm font-semibold text-slate-300 flex items-center gap-2">
          <KeyRound size={15} className="text-cg-accent" /> Change Password
        </h3>
        <div>
          <label className="block text-xs text-slate-400 mb-1">Current Password</label>
          <input type="password" required className="cg-input w-full" value={password.currentPassword} onChange={(e) => setPassword({ ...password, currentPassword: e.target.value })} />
        </div>
        <div>
          <label className="block text-xs text-slate-400 mb-1">New Password</label>
          <input type="password" required minLength={8} className="cg-input w-full" value={password.newPassword} onChange={(e) => setPassword({ ...password, newPassword: e.target.value })} />
        </div>
        <button type="submit" className="cg-btn-primary">Update Password</button>
      </form>

      {prefs && (
        <div className="cg-card space-y-3">
          <h3 className="text-sm font-semibold text-slate-300 flex items-center gap-2">
            <Bell size={15} className="text-cg-accent" /> Notification Preferences
          </h3>
          {[
            ['emailAlerts', 'Email Alerts'],
            ['criticalAlerts', 'Critical Threat Alerts'],
            ['dashboardAlerts', 'Dashboard Notifications'],
            ['weeklySummary', 'Weekly Summary Email'],
          ].map(([field, label]) => (
            <label key={field} className="flex items-center justify-between text-sm text-slate-300 py-1">
              {label}
              <input type="checkbox" checked={!!prefs[field]} onChange={() => handlePrefsChange(field)} className="accent-cg-accent w-4 h-4 cursor-pointer" />
            </label>
          ))}
        </div>
      )}

      <div className="cg-card space-y-3">
        <h3 className="text-sm font-semibold text-slate-300 flex items-center gap-2">
          <Webhook size={15} className="text-cg-accent" /> API Configuration
        </h3>
        <div>
          <label className="block text-xs text-slate-400 mb-1">API Key</label>
          <div className="flex gap-2">
            <input readOnly className="cg-input w-full font-mono text-xs" value={apiConfig?.apiKey || 'No API key generated yet'} />
            <button onClick={handleRegenerateKey} className="cg-btn-secondary flex items-center gap-1 whitespace-nowrap">
              <RefreshCw size={14} /> Regenerate
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
