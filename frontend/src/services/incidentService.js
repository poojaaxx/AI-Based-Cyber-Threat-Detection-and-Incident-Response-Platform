import api from './api';

export const incidentService = {
  getIncidents: (params) => api.get('/incidents', { params }),
  getRecent: () => api.get('/incidents/recent'),
  getById: (id) => api.get(`/incidents/${id}`),
  create: (payload) => api.post('/incidents', payload),
  update: (id, payload) => api.patch(`/incidents/${id}`, payload),
  getComments: (id) => api.get(`/incidents/${id}/comments`),
  addComment: (id, comment) => api.post(`/incidents/${id}/comments`, { comment }),
  getTimeline: (id) => api.get(`/incidents/${id}/timeline`),
  getAssignableUsers: () => api.get('/incidents/assignable-users'),
};
