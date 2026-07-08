import api from './api';

export const settingsService = {
  getCurrentUser: () => api.get('/users/me'),
  updateProfile: (payload) => api.put('/settings/profile', payload),
  changePassword: (payload) => api.put('/settings/password', payload),
  getNotificationPreferences: () => api.get('/settings/notification-preferences'),
  updateNotificationPreferences: (payload) => api.put('/settings/notification-preferences', payload),
  getApiConfiguration: () => api.get('/settings/api-configuration'),
  regenerateApiKey: () => api.post('/settings/api-configuration/regenerate'),
};
