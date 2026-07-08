import api from './api';

export const simulationService = {
  run: (payload) => api.post('/simulations', payload),
  search: (params) => api.get('/simulations', { params }),
  getById: (id) => api.get(`/simulations/${id}`),
  remove: (id) => api.delete(`/simulations/${id}`),
};
