import { API_BASE_URL } from './api';

/**
 * Connects to the backend's SSE stream using fetch() + a manual ReadableStream
 * reader instead of the native EventSource API. EventSource cannot send custom
 * headers, so it can't carry the existing JWT Authorization header; fetch()
 * can, which means this reuses the exact same auth flow as every other request
 * with no backend security changes required.
 *
 * Returns a disconnect() function. Automatically reconnects with backoff if the
 * stream drops (network blip, backend restart, token refresh, etc).
 */
export function connectEventStream({ onNotification, onDashboardUpdate, onConnectionChange }) {
  let aborted = false;
  let controller = null;
  let retryDelay = 2000;

  const parseChunk = (raw) => {
    const eventMatch = /(?:^|\n)event:\s*(.+)/.exec(raw);
    const dataMatch = /(?:^|\n)data:\s*(.+)/.exec(raw);
    if (!dataMatch) return;
    const eventName = eventMatch?.[1]?.trim() || 'message';
    let payload;
    try {
      payload = JSON.parse(dataMatch[1]);
    } catch {
      payload = dataMatch[1];
    }
    if (eventName === 'connected') onConnectionChange?.(true);
    else if (eventName === 'notification') onNotification?.(payload);
    else if (eventName === 'dashboard-update') onDashboardUpdate?.(payload);
  };

  const open = async () => {
    if (aborted) return;
    controller = new AbortController();
    const token = localStorage.getItem('accessToken');

    try {
      const response = await fetch(`${API_BASE_URL}/events/stream`, {
        headers: { Authorization: `Bearer ${token}`, Accept: 'text/event-stream' },
        signal: controller.signal,
      });
      if (!response.ok || !response.body) throw new Error(`SSE connect failed: ${response.status}`);

      retryDelay = 2000; // reset backoff on a successful connection
      const reader = response.body.getReader();
      const decoder = new TextDecoder();
      let buffer = '';

      while (!aborted) {
        const { done, value } = await reader.read();
        if (done) break;
        buffer += decoder.decode(value, { stream: true });
        const chunks = buffer.split('\n\n');
        buffer = chunks.pop();
        chunks.forEach(parseChunk);
      }
    } catch (err) {
      if (aborted || err.name === 'AbortError') return;
    }

    onConnectionChange?.(false);
    if (!aborted) {
      setTimeout(open, retryDelay);
      retryDelay = Math.min(retryDelay * 1.5, 30000);
    }
  };

  open();

  return () => {
    aborted = true;
    controller?.abort();
  };
}
