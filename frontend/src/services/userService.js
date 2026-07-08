import api from './api';

export const userService = {
  getAllUsers: (params) => api.get('/users', { params }),
  getUser: (id) => api.get(`/users/${id}`),
  createUser: (payload) => api.post('/users', payload),
  updateUser: (id, payload) => api.put(`/users/${id}`, payload),
  deleteUser: (id) => api.delete(`/users/${id}`),
  resetPassword: (id) => api.post(`/users/${id}/reset-password`),
  updateStatus: (id, status) => api.patch(`/users/${id}/status`, null, { params: { status } }),
  updateRoles: (id, roles) => api.patch(`/users/${id}/roles`, roles),
};
