import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';

// Exercise the production transport with an in-memory HTTP stream; no test data enters the app/database.
test('SSE parses split CRLF frames, reconnects, detects stale streams, and cleans up', async () => {
  const source = (await readFile(new URL('../src/services/eventStreamService.js', import.meta.url), 'utf8'))
    .replace("import api, { API_BASE_URL } from './api';", "const api = globalThis.testApi; const API_BASE_URL = 'http://test.invalid';");
  const { connectEventStream } = await import(`data:text/javascript;base64,${Buffer.from(source).toString('base64')}`);
  const originals = Object.fromEntries(['fetch', 'localStorage', 'window', 'CustomEvent', 'setTimeout', 'clearTimeout'].map(k => [k, globalThis[k]]));
  const timers = new Map();
  let nextId = 0;
  const states = [], events = [], connected = [];
  let stream;
  let requests = 0;
  globalThis.localStorage = { getItem: () => 'test-token' };
  globalThis.CustomEvent = class { constructor(type) { this.type = type; } };
  globalThis.window = { dispatchEvent: e => connected.push(e.type) };
  globalThis.setTimeout = (fn, ms) => { timers.set(++nextId, { fn, ms }); return nextId; };
  globalThis.clearTimeout = id => timers.delete(id);
  globalThis.fetch = async (_url, { signal, headers }) => {
    requests++;
    assert.equal(headers.Authorization, 'Bearer test-token');
    return new Response(new ReadableStream({ start(controller) {
      stream = controller;
      signal.addEventListener('abort', () => { try { controller.error(new DOMException('Aborted', 'AbortError')); } catch {} });
    } }), { headers: { 'Content-Type': 'text/event-stream' } });
  };
  const tick = () => new Promise(resolve => setImmediate(resolve));
  const send = text => stream.enqueue(new TextEncoder().encode(text));
  const runTimer = ms => {
    const [id, timer] = [...timers].find(([, value]) => value.ms === ms);
    timers.delete(id); timer.fn();
  };
  let disconnect;
  try {
    disconnect = connectEventStream({ onSecurityEvent: event => events.push(event), onConnectionChange: value => states.push(value) });
    await tick();
    send('event: connected\r\ndata: ok\r\n\r\nevent: security-event\r\ndata: {"id":');
    send('17,"outcome":"SUCCESS"}\r\n\r\n');
    await tick();
    assert.equal(states.at(-1), 'CONNECTED');
    assert.deepEqual(events, [{ id: 17, outcome: 'SUCCESS' }]);
    stream.close(); await tick();
    assert.equal(states.at(-1), 'RECONNECTING');
    runTimer(2000); await tick();
    send('event: connected\ndata: ok\n\n'); await tick();
    assert.equal(requests, 2);
    assert.equal(connected.filter(x => x === 'cg:stream-connected').length, 2);
    runTimer(45000); await tick();
    assert.equal(states.at(-1), 'RECONNECTING');
    disconnect();
    assert.equal(timers.size, 0);
    globalThis.localStorage = { getItem: () => null };
    disconnect = connectEventStream({ onConnectionChange: value => states.push(value) });
    assert.equal(states.at(-1), 'DISCONNECTED');
  } finally {
    disconnect?.();
    Object.assign(globalThis, originals);
  }
});
