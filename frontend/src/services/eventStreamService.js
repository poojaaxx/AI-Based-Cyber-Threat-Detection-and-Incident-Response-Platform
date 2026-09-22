import api, { API_BASE_URL } from './api';

// One authenticated stream for the app. Timers only detect transport failure; they never generate events.
export function connectEventStream({ onNotification, onDashboardUpdate, onSecurityEvent, onCollectorStatus, onConnectionChange }) {
  let stopped = false;
  let controller;
  let retryTimer;
  let staleTimer;
  let delay = 2000;
  const state = (value) => onConnectionChange?.(value);
  const touch = () => {
    clearTimeout(staleTimer);
    staleTimer = setTimeout(() => controller?.abort(), 45000);
  };
  const parse = (frame) => {
    const lines = frame.split(/\r?\n/);
    const name = lines.find((line) => line.startsWith('event:'))?.slice(6).trim() || 'message';
    const data = lines.filter((line) => line.startsWith('data:')).map((line) => line.slice(5).trimStart()).join('\n');
    if (!data) return;
    let payload;
    try { payload = JSON.parse(data); } catch { payload = data; }
    touch();
    if (name === 'connected') {
      delay = 2000;
      state('CONNECTED');
      window.dispatchEvent(new CustomEvent('cg:stream-connected'));
    } else if (name === 'notification') onNotification?.(payload);
    else if (name === 'dashboard-update') onDashboardUpdate?.(payload);
    else if (name === 'security-event') onSecurityEvent?.(payload);
    else if (name === 'collector-status') onCollectorStatus?.(payload);
  };
  const open = async () => {
    if (stopped) return;
    const token = localStorage.getItem('accessToken');
    if (!token) { state('DISCONNECTED'); return; }
    state('RECONNECTING');
    controller = new AbortController();
    touch();
    let terminal = false;
    try {
      const response = await fetch(`${API_BASE_URL}/events/stream`, {
        headers: { Authorization: `Bearer ${token}`, Accept: 'text/event-stream' }, signal: controller.signal,
      });
      if (response.status === 401) {
        // Use the existing Axios refresh queue instead of racing a separate token refresh.
        await api.get('/notifications/unread-count');
        throw new Error('Reconnect with refreshed token');
      }
      if (response.status === 403) { terminal = true; throw new Error('Access denied'); }
      if (!response.ok || !response.body) throw new Error('Stream unavailable');
      const reader = response.body.getReader();
      const decoder = new TextDecoder();
      let buffer = '';
      try {
        while (!stopped) {
          const { done, value } = await reader.read();
          if (done) break;
          buffer += decoder.decode(value, { stream: true });
          const frames = buffer.split(/\r?\n\r?\n/);
          buffer = frames.pop();
          frames.forEach(parse);
        }
      } finally { reader.releaseLock(); }
    } catch { /* Transport failures are represented by state and retried below. */ }
    clearTimeout(staleTimer);
    controller?.abort();
    if (stopped) return;
    if (terminal || !localStorage.getItem('accessToken')) { state('DISCONNECTED'); return; }
    state('RECONNECTING');
    retryTimer = setTimeout(open, delay);
    delay = Math.min(delay * 1.5, 30000);
  };
  open();
  return () => {
    stopped = true;
    clearTimeout(retryTimer);
    clearTimeout(staleTimer);
    controller?.abort();
  };
}
