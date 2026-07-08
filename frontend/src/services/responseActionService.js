import api from './api';

export const responseActionService = {
  getActions: (params) => api.get('/response-actions', { params }),
  blockIp: (target, reason) => api.post('/response-actions/block-ip', { target, reason }),
  disableUser: (userId, reason) => api.post(`/response-actions/disable-user/${userId}`, { reason }),
  quarantine: (threatId) => api.post(`/response-actions/quarantine/${threatId}`),
};
