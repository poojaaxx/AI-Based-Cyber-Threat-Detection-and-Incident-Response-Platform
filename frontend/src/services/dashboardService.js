import api from './api';

export const dashboardService = {
  getSummary: () => api.get('/dashboard/summary'),
  getRiskScore: () => api.get('/dashboard/risk-score'),
  getSecuritySummary: () => api.get('/dashboard/security-summary'),
};
