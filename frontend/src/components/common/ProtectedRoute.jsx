import { Navigate, Outlet } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import Forbidden from '../../pages/Forbidden';

export default function ProtectedRoute({ roles }) {
  const { user, loading } = useAuth();

  if (loading) {
    return (
      <div className="flex items-center justify-center h-screen bg-cg-bg text-cg-accent">
        Loading...
      </div>
    );
  }

  if (!user) {
    return <Navigate to="/login" replace />;
  }

  if (user.mustChangePassword) {
    return <Navigate to="/force-password-change" replace />;
  }

  if (roles && !roles.some((r) => user.roles?.includes(r))) {
    return <Forbidden />;
  }

  return <Outlet />;
}
