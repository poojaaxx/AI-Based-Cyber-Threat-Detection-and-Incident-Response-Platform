import api from './api';

export const monitoringService = {
  getSystemLogs: (params) => api.get('/monitoring/system-logs', { params }),
  getLoginAttempts: (params) => api.get('/monitoring/login-attempts', { params }),
  getNetworkEvents: (params) => api.get('/monitoring/network-events', { params }),
};
