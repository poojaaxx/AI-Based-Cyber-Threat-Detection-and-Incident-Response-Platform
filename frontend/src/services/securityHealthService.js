import api from './api';

export const securityHealthService = {
  getHealth: () => api.get('/security-health'),
};
