import api from './api';

export const assistantService = {
  chat: (message, sessionId) => api.post('/assistant/chat', { message, sessionId }),
  getSessions: () => api.get('/assistant/sessions'),
  getMessages: (sessionId) => api.get(`/assistant/sessions/${sessionId}/messages`),
};
