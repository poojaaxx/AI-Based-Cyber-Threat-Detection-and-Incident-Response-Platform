import api from './api';

export const threatService = {
  getThreats: (params) => api.get('/threats', { params }),
  getRecent: () => api.get('/threats/recent'),
  getById: (id) => api.get(`/threats/${id}`),
  updateStatus: (id, status) => api.patch(`/threats/${id}/status`, null, { params: { status } }),
  detect: (payload) => api.post('/threats/detect', payload),
  explain: (id) => api.get(`/threats/${id}/explain`),
  investigate: (id) => api.get(`/threats/${id}/investigation`),
  predictTemporal: (records) => api.post('/threats/predict-temporal', { records }),
};
