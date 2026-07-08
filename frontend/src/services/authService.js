import api from './api';

export const authService = {
  login: (usernameOrEmail, password) => api.post('/auth/login', { usernameOrEmail, password }),
  register: (payload) => api.post('/auth/register', payload),
  refresh: (refreshToken) => api.post('/auth/refresh', { refreshToken }),
  logout: (refreshToken) => api.post('/auth/logout', { refreshToken }),
};
