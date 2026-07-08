import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { Plus, Search, KeyRound, Pencil, Trash2, Eye, ArrowUpDown, Users as UsersIcon, Inbox } from 'lucide-react';
import { userService } from '../services/userService';
import { StatusBadge } from '../components/common/Badge';
import Modal from '../components/common/Modal';
import { Skeleton } from '../components/common/Skeleton';
import EmptyState from '../components/common/EmptyState';
import { useAuth } from '../context/AuthContext';

const STATUSES = ['ACTIVE', 'DISABLED', 'LOCKED'];
const ROLES = ['ROLE_ADMIN', 'ROLE_ANALYST', 'ROLE_USER'];
const SORT_OPTIONS = [
  { value: 'username', label: 'Username' },
  { value: 'fullName', label: 'Name' },
  { value: 'createdAt', label: 'Recently Created' },
];

const emptyForm = {
  fullName: '', username: '', email: '', password: '', confirmPassword: '',
  department: '', phone: '', role: 'ROLE_USER', status: 'ACTIVE',
};

export default function Users() {
  const { user: currentUser } = useAuth();
  const [users, setUsers] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const [filters, setFilters] = useState({ search: '', role: '', status: '', department: '' });
  const [sortBy, setSortBy] = useState('username');
  const [sortDir, setSortDir] = useState('asc');

  const [modalMode, setModalMode] = useState(null); // 'add' | 'edit' | null
  const [form, setForm] = useState(emptyForm);
  const [editingId, setEditingId] = useState(null);
  const [formError, setFormError] = useState('');
  const [saving, setSaving] = useState(false);

  const [deleteTarget, setDeleteTarget] = useState(null);
  const [resetResult, setResetResult] = useState(null);

  const load = () => {
    setLoading(true);
    setError('');
    userService.getAllUsers({
      size: 50,
      search: filters.search || undefined,
      role: filters.role || undefined,
      status: filters.status || undefined,
      department: filters.department || undefined,
      sortBy,
      sortDir,
    })
      .then(({ data }) => setUsers(data.content))
      .catch(() => setError('Failed to load users.'))
      .finally(() => setLoading(false));
  };

  useEffect(() => { load(); }, [sortBy, sortDir]); // eslint-disable-line react-hooks/exhaustive-deps

  const handleSearchSubmit = (e) => {
    e.preventDefault();
    load();
  };

  const openAdd = () => {
    setForm(emptyForm);
    setFormError('');
    setEditingId(null);
    setModalMode('add');
  };

  const openEdit = (u) => {
    setForm({
      fullName: u.fullName, username: u.username, email: u.email,
      password: '', confirmPassword: '',
      department: u.department || '', phone: u.phone || '',
      role: u.roles?.[0] || 'ROLE_USER', status: u.status,
    });
    setFormError('');
    setEditingId(u.id);
    setModalMode('edit');
  };

  const closeModal = () => setModalMode(null);

  const handleFormSubmit = async (e) => {
    e.preventDefault();
    setFormError('');
    if (modalMode === 'add' && form.password !== form.confirmPassword) {
      setFormError('Password and confirm password do not match');
      return;
    }
    setSaving(true);
    try {
      if (modalMode === 'add') {
        await userService.createUser({
          fullName: form.fullName, username: form.username, email: form.email,
          password: form.password, confirmPassword: form.confirmPassword,
          department: form.department, phone: form.phone, role: form.role,
        });
      } else {
        await userService.updateUser(editingId, {
          fullName: form.fullName, email: form.email, department: form.department,
          phone: form.phone, status: form.status, role: form.role,
        });
      }
      closeModal();
      load();
    } catch (err) {
      setFormError(err.response?.data?.message || 'Failed to save user.');
    } finally {
      setSaving(false);
    }
  };

  const handleStatusChange = async (id, status) => {
    await userService.updateStatus(id, status);
    load();
  };

  const handleResetPassword = async (u) => {
    const { data } = await userService.resetPassword(u.id);
    setResetResult({ username: u.username, ...data });
  };

  const confirmDelete = async () => {
    try {
      await userService.deleteUser(deleteTarget.id);
      setDeleteTarget(null);
      load();
    } catch (err) {
      setError(err.response?.data?.message || 'Failed to delete user.');
      setDeleteTarget(null);
    }
  };

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between flex-wrap gap-3">
        <div>
          <h2 className="text-lg font-semibold text-slate-100 flex items-center gap-2">
            <UsersIcon size={20} className="text-cg-accent" /> User Management
          </h2>
          <p className="text-sm text-slate-500 mt-1">Manage accounts, roles, and access across the platform.</p>
        </div>
        <button onClick={openAdd} className="cg-btn-primary flex items-center gap-2">
          <Plus size={16} /> Add User
        </button>
      </div>

      {error && (
        <div className="bg-cg-danger/10 border border-cg-danger/30 text-cg-danger text-sm rounded-md px-3 py-2">
          {error}
        </div>
      )}

      <div className="cg-card space-y-3">
        <form onSubmit={handleSearchSubmit} className="flex flex-wrap gap-3 items-end">
          <div className="flex-1 min-w-[200px]">
            <label className="block text-xs text-slate-400 mb-1">Search</label>
            <div className="relative">
              <Search size={14} className="absolute left-2.5 top-1/2 -translate-y-1/2 text-slate-500" />
              <input
                className="cg-input w-full pl-8"
                placeholder="Name, username or email..."
                value={filters.search}
                onChange={(e) => setFilters({ ...filters, search: e.target.value })}
              />
            </div>
          </div>
          <div>
            <label className="block text-xs text-slate-400 mb-1">Role</label>
            <select className="cg-input" value={filters.role} onChange={(e) => setFilters({ ...filters, role: e.target.value })}>
              <option value="">All Roles</option>
              {ROLES.map((r) => <option key={r} value={r}>{r.replace('ROLE_', '')}</option>)}
            </select>
          </div>
          <div>
            <label className="block text-xs text-slate-400 mb-1">Status</label>
            <select className="cg-input" value={filters.status} onChange={(e) => setFilters({ ...filters, status: e.target.value })}>
              <option value="">All Statuses</option>
              {STATUSES.map((s) => <option key={s} value={s}>{s}</option>)}
            </select>
          </div>
          <div>
            <label className="block text-xs text-slate-400 mb-1">Department</label>
            <input
              className="cg-input"
              placeholder="e.g. IT Security"
              value={filters.department}
              onChange={(e) => setFilters({ ...filters, department: e.target.value })}
            />
          </div>
          <button type="submit" className="cg-btn-secondary">Apply</button>
        </form>

        <div className="flex items-center gap-2 pt-1">
          <ArrowUpDown size={14} className="text-slate-500" />
          <span className="text-xs text-slate-400">Sort by</span>
          <select className="cg-input text-xs" value={sortBy} onChange={(e) => setSortBy(e.target.value)}>
            {SORT_OPTIONS.map((o) => <option key={o.value} value={o.value}>{o.label}</option>)}
          </select>
          <select className="cg-input text-xs" value={sortDir} onChange={(e) => setSortDir(e.target.value)}>
            <option value="asc">Ascending</option>
            <option value="desc">Descending</option>
          </select>
        </div>
      </div>

      <div className="cg-card overflow-x-auto !p-0">
        <table className="w-full text-sm">
          <thead>
            <tr className="text-left text-slate-400 border-b border-cg-border">
              <th className="py-3 pl-5 pr-4">Name</th>
              <th className="py-3 pr-4">Username</th>
              <th className="py-3 pr-4">Email</th>
              <th className="py-3 pr-4">Role</th>
              <th className="py-3 pr-4">Status</th>
              <th className="py-3 pr-4">Actions</th>
            </tr>
          </thead>
          <tbody>
            {loading ? (
              Array.from({ length: 8 }).map((_, i) => (
                <tr key={i} className="border-b border-cg-border last:border-0">
                  <td className="py-2.5 pl-5 pr-4" colSpan={6}><Skeleton className="h-4 w-full" /></td>
                </tr>
              ))
            ) : users.length === 0 ? (
              <tr><td colSpan={6}><EmptyState icon={Inbox} title="No users found" message="No users match the current filters." /></td></tr>
            ) : users.map((u) => {
              const isSelf = u.username === currentUser?.username;
              return (
                <tr key={u.id} className="cg-table-row">
                  <td className="py-2.5 pl-5 pr-4 text-slate-200">{u.fullName}</td>
                  <td className="py-2.5 pr-4 text-slate-400">{u.username}</td>
                  <td className="py-2.5 pr-4 text-slate-400">{u.email}</td>
                  <td className="py-2.5 pr-4 text-slate-400">{u.roles?.map((r) => r.replace('ROLE_', '')).join(', ')}</td>
                  <td className="py-2.5 pr-4">
                    <select
                      className="cg-input text-xs"
                      value={u.status}
                      onChange={(e) => handleStatusChange(u.id, e.target.value)}
                    >
                      {STATUSES.map((s) => <option key={s} value={s}>{s}</option>)}
                    </select>
                    <div className="mt-1"><StatusBadge status={u.status} /></div>
                  </td>
                  <td className="py-2.5 pr-4">
                    <div className="flex items-center gap-3 text-slate-400">
                      <Link to={`/users/${u.id}`} title="View Profile" className="hover:text-cg-accent">
                        <Eye size={16} />
                      </Link>
                      <button title="Edit" onClick={() => openEdit(u)} className="hover:text-cg-accent">
                        <Pencil size={16} />
                      </button>
                      <button title="Reset Password" onClick={() => handleResetPassword(u)} className="hover:text-cg-warning">
                        <KeyRound size={16} />
                      </button>
                      <button
                        title={isSelf ? "You cannot delete your own account" : "Delete"}
                        disabled={isSelf}
                        onClick={() => setDeleteTarget(u)}
                        className={isSelf ? 'text-slate-700 cursor-not-allowed' : 'hover:text-cg-danger'}
                      >
                        <Trash2 size={16} />
                      </button>
                    </div>
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>

      {modalMode && (
        <Modal title={modalMode === 'add' ? 'Add User' : `Edit User: ${form.username}`} onClose={closeModal}>
          <form onSubmit={handleFormSubmit} className="space-y-3">
            {formError && (
              <div className="bg-cg-danger/10 border border-cg-danger/30 text-cg-danger text-sm rounded-md px-3 py-2">
                {formError}
              </div>
            )}

            <div>
              <label className="block text-xs text-slate-400 mb-1">Full Name</label>
              <input required maxLength={120} className="cg-input w-full" value={form.fullName} onChange={(e) => setForm({ ...form, fullName: e.target.value })} />
            </div>

            <div className="grid grid-cols-2 gap-3">
              <div>
                <label className="block text-xs text-slate-400 mb-1">Username</label>
                <input
                  required
                  disabled={modalMode === 'edit'}
                  minLength={3}
                  maxLength={60}
                  className="cg-input w-full disabled:opacity-60"
                  value={form.username}
                  onChange={(e) => setForm({ ...form, username: e.target.value })}
                />
              </div>
              <div>
                <label className="block text-xs text-slate-400 mb-1">Email</label>
                <input required type="email" maxLength={150} className="cg-input w-full" value={form.email} onChange={(e) => setForm({ ...form, email: e.target.value })} />
              </div>
            </div>

            {modalMode === 'add' && (
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block text-xs text-slate-400 mb-1">Password</label>
                  <input required type="password" minLength={8} maxLength={100} className="cg-input w-full" value={form.password} onChange={(e) => setForm({ ...form, password: e.target.value })} />
                </div>
                <div>
                  <label className="block text-xs text-slate-400 mb-1">Confirm Password</label>
                  <input required type="password" minLength={8} maxLength={100} className="cg-input w-full" value={form.confirmPassword} onChange={(e) => setForm({ ...form, confirmPassword: e.target.value })} />
                </div>
              </div>
            )}

            <div className="grid grid-cols-2 gap-3">
              <div>
                <label className="block text-xs text-slate-400 mb-1">Department</label>
                <input maxLength={100} className="cg-input w-full" value={form.department} onChange={(e) => setForm({ ...form, department: e.target.value })} />
              </div>
              <div>
                <label className="block text-xs text-slate-400 mb-1">Phone Number</label>
                <input maxLength={30} className="cg-input w-full" value={form.phone} onChange={(e) => setForm({ ...form, phone: e.target.value })} />
              </div>
            </div>

            <div className="grid grid-cols-2 gap-3">
              <div>
                <label className="block text-xs text-slate-400 mb-1">Role</label>
                <select className="cg-input w-full" value={form.role} onChange={(e) => setForm({ ...form, role: e.target.value })}>
                  {ROLES.map((r) => <option key={r} value={r}>{r.replace('ROLE_', '')}</option>)}
                </select>
              </div>
              {modalMode === 'edit' && (
                <div>
                  <label className="block text-xs text-slate-400 mb-1">Status</label>
                  <select className="cg-input w-full" value={form.status} onChange={(e) => setForm({ ...form, status: e.target.value })}>
                    {STATUSES.map((s) => <option key={s} value={s}>{s}</option>)}
                  </select>
                </div>
              )}
            </div>

            <button type="submit" disabled={saving} className="cg-btn-primary w-full">
              {saving ? 'Saving...' : modalMode === 'add' ? 'Create User' : 'Save Changes'}
            </button>
          </form>
        </Modal>
      )}

      {deleteTarget && (
        <Modal title="Confirm Deletion" onClose={() => setDeleteTarget(null)} maxWidth="max-w-sm">
          <p className="text-sm text-slate-300 mb-4">
            Are you sure you want to permanently delete <span className="font-semibold text-slate-100">{deleteTarget.username}</span>? This action cannot be undone.
          </p>
          <div className="flex gap-3">
            <button onClick={() => setDeleteTarget(null)} className="cg-btn-secondary flex-1">Cancel</button>
            <button onClick={confirmDelete} className="bg-cg-danger text-white font-semibold px-4 py-2 rounded-md hover:bg-cg-danger/90 transition flex-1">
              Delete Permanently
            </button>
          </div>
        </Modal>
      )}

      {resetResult && (
        <Modal title="Temporary Password Generated" onClose={() => setResetResult(null)} maxWidth="max-w-md">
          <p className="text-sm text-slate-300 mb-3">
            A temporary password was generated for <span className="font-semibold text-slate-100">{resetResult.username}</span>.
            Share it securely — it will not be shown again. The user must change it on next login.
          </p>
          <div className="cg-input w-full font-mono text-center text-lg tracking-wider text-cg-accent mb-4">
            {resetResult.temporaryPassword}
          </div>
          <button onClick={() => setResetResult(null)} className="cg-btn-primary w-full">Done</button>
        </Modal>
      )}
    </div>
  );
}
