import { createContext, useContext, useEffect, useState } from 'react';
import { connectEventStream } from '../services/eventStreamService';

const EventStreamContext = createContext({ connected: false });

/**
 * Opens exactly one SSE connection for the authenticated shell and republishes
 * events as window CustomEvents. Topbar (live status pill) and NotificationCenter
 * (toast list) both read from this single connection instead of each opening
 * their own, so navigating the app never doubles up backend SSE subscriptions.
 */
export function EventStreamProvider({ children }) {
  const [connected, setConnected] = useState(false);

  useEffect(() => {
    const disconnect = connectEventStream({
      onNotification: (payload) => window.dispatchEvent(new CustomEvent('cg:notification', { detail: payload })),
      onDashboardUpdate: (payload) => window.dispatchEvent(new CustomEvent('cg:dashboard-update', { detail: payload })),
      onConnectionChange: setConnected,
    });
    return disconnect;
  }, []);

  return <EventStreamContext.Provider value={{ connected }}>{children}</EventStreamContext.Provider>;
}

export function useEventStreamStatus() {
  return useContext(EventStreamContext);
}
