import { lazy, Suspense } from 'react';
import { Navigate, Route, Routes } from 'react-router-dom';
import ProtectedRoute from './components/common/ProtectedRoute';
import DashboardLayout from './components/layout/DashboardLayout';
import Login from './pages/Login';

// Every other page is route-split via React.lazy() - each is only fetched the
// first time its route is visited, keeping the initial bundle small instead of
// shipping the entire app (charts, PDF-adjacent view code, etc.) up front.
const Register = lazy(() => import('./pages/Register'));
const ForcePasswordChange = lazy(() => import('./pages/ForcePasswordChange'));
const Dashboard = lazy(() => import('./pages/Dashboard'));
const Threats = lazy(() => import('./pages/Threats'));
const ThreatDetail = lazy(() => import('./pages/ThreatDetail'));
const Investigation = lazy(() => import('./pages/Investigation'));
const Incidents = lazy(() => import('./pages/Incidents'));
const IncidentDetail = lazy(() => import('./pages/IncidentDetail'));
const ThreatIntel = lazy(() => import('./pages/ThreatIntel'));
const Monitoring = lazy(() => import('./pages/Monitoring'));
const Reports = lazy(() => import('./pages/Reports'));
const Notifications = lazy(() => import('./pages/Notifications'));
const AuditLogs = lazy(() => import('./pages/AuditLogs'));
const Settings = lazy(() => import('./pages/Settings'));
const Assistant = lazy(() => import('./pages/Assistant'));
const Users = lazy(() => import('./pages/Users'));
const UserProfile = lazy(() => import('./pages/UserProfile'));
const Simulate = lazy(() => import('./pages/Simulate'));
const Analytics = lazy(() => import('./pages/Analytics'));
const SecurityHealth = lazy(() => import('./pages/SecurityHealth'));
const NotFound = lazy(() => import('./pages/NotFound'));

function RouteLoadingFallback() {
  return (
    <div className="min-h-screen bg-cg-bg flex items-center justify-center text-cg-accent">
      Loading...
    </div>
  );
}

export default function App() {
  return (
    <Suspense fallback={<RouteLoadingFallback />}>
      <Routes>
        <Route path="/login" element={<Login />} />
        <Route path="/register" element={<Register />} />
        <Route path="/force-password-change" element={<ForcePasswordChange />} />

        <Route element={<ProtectedRoute />}>
          <Route element={<DashboardLayout title="Security Operations Center" />}>
            <Route path="/dashboard" element={<Dashboard />} />
            <Route path="/threats" element={<Threats />} />
            <Route path="/threats/:id" element={<ThreatDetail />} />
            <Route path="/threats/:id/investigation" element={<Investigation />} />
            <Route path="/notifications" element={<Notifications />} />
            <Route path="/assistant" element={<Assistant />} />
            <Route path="/profile" element={<UserProfile />} />
          </Route>
        </Route>

        <Route element={<ProtectedRoute roles={['ROLE_ADMIN', 'ROLE_ANALYST']} />}>
          <Route element={<DashboardLayout title="Security Operations Center" />}>
            <Route path="/incidents" element={<Incidents />} />
            <Route path="/incidents/:id" element={<IncidentDetail />} />
            <Route path="/analytics" element={<Analytics />} />
            <Route path="/threat-intel" element={<ThreatIntel />} />
            <Route path="/reports" element={<Reports />} />
            <Route path="/audit-logs" element={<AuditLogs />} />
          </Route>
          <Route element={<DashboardLayout title="Real-Time Monitoring" />}>
            <Route path="/monitoring" element={<Monitoring />} />
          </Route>
          <Route element={<DashboardLayout title="Security Health" />}>
            <Route path="/security-health" element={<SecurityHealth />} />
          </Route>
          <Route element={<DashboardLayout title="Simulate Threat" />}>
            <Route path="/simulate" element={<Simulate />} />
          </Route>
        </Route>

        <Route element={<ProtectedRoute roles={['ROLE_ADMIN']} />}>
          <Route element={<DashboardLayout title="Administration" />}>
            <Route path="/users" element={<Users />} />
            <Route path="/users/:id" element={<UserProfile />} />
            <Route path="/settings" element={<Settings />} />
          </Route>
        </Route>

        <Route path="/" element={<Navigate to="/dashboard" replace />} />
        <Route path="*" element={<NotFound />} />
      </Routes>
    </Suspense>
  );
}
