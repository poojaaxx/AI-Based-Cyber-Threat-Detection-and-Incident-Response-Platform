import { useEffect, useState } from 'react';
import { useParams, Link } from 'react-router-dom';
import { UserCircle2, ArrowLeft, AlertTriangle } from 'lucide-react';
import { userService } from '../services/userService';
import api from '../services/api';
import { StatusBadge } from '../components/common/Badge';
import { Skeleton } from '../components/common/Skeleton';
import EmptyState from '../components/common/EmptyState';

function formatDate(value) {
  if (!value) return 'Never';
  return new Date(value).toLocaleString();
}

export default function UserProfile() {
  const { id } = useParams();
  const [profile, setProfile] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    setLoading(true);
    setError('');
    (id ? userService.getUser(id) : api.get('/users/me'))
      .then(({ data }) => setProfile(data))
      .catch(() => setError('Failed to load profile.'))
      .finally(() => setLoading(false));
  }, [id]);

  if (loading) {
    return (
      <div className="space-y-4 max-w-2xl">
        <div className="cg-card">
          <div className="flex items-center gap-4 mb-6">
            <Skeleton className="h-14 w-14 rounded-full" />
            <div className="space-y-2">
              <Skeleton className="h-4 w-40" />
              <Skeleton className="h-3 w-24" />
            </div>
          </div>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            {Array.from({ length: 8 }).map((_, i) => <Skeleton key={i} className="h-10 w-full" />)}
          </div>
        </div>
      </div>
    );
  }
  if (error || !profile) {
    return <EmptyState icon={AlertTriangle} title="Profile not found" message={error || 'This profile could not be loaded.'} />;
  }

  return (
    <div className="space-y-4 max-w-2xl">
      {id && (
        <Link to="/users" className="text-sm text-slate-400 hover:text-cg-accent flex items-center gap-1 w-fit">
          <ArrowLeft size={14} /> Back to User Management
        </Link>
      )}

      <div className="cg-card">
        <div className="flex items-center gap-4 mb-6">
          <UserCircle2 size={56} className="text-cg-accent" />
          <div>
            <h2 className="text-lg font-semibold text-slate-100">{profile.fullName}</h2>
            <p className="text-sm text-slate-500">@{profile.username}</p>
          </div>
          <div className="ml-auto">
            <StatusBadge status={profile.status} />
          </div>
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 text-sm">
          <ProfileField label="Email" value={profile.email} />
          <ProfileField label="Department" value={profile.department || '—'} />
          <ProfileField label="Phone" value={profile.phone || '—'} />
          <ProfileField label="Role" value={profile.roles?.map((r) => r.replace('ROLE_', '')).join(', ')} />
          <ProfileField label="Account Status" value={profile.status} />
          <ProfileField label="Last Login" value={formatDate(profile.lastLoginAt)} />
          <ProfileField label="Failed Login Attempts" value={profile.failedLoginAttempts ?? 0} />
          <ProfileField label="Created Date" value={formatDate(profile.createdAt)} />
        </div>
      </div>
    </div>
  );
}

function ProfileField({ label, value }) {
  return (
    <div>
      <p className="text-xs uppercase tracking-wide text-slate-500 mb-1">{label}</p>
      <p className="text-slate-200">{value}</p>
    </div>
  );
}
