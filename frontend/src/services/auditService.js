import api from './api';

export const auditService = {
  getAuditLogs: (params) => api.get('/audit-logs', { params }),
};
