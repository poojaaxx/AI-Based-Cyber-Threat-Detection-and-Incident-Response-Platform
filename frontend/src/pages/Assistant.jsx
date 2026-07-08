import { useEffect, useRef, useState } from 'react';
import { Bot, Send, User } from 'lucide-react';
import { assistantService } from '../services/assistantService';

export default function Assistant() {
  const [messages, setMessages] = useState([
    { sender: 'ASSISTANT', message: "Hi! I'm your AI Security Assistant. Ask me about any threat type, CVE, or mitigation strategy." },
  ]);
  const [input, setInput] = useState('');
  const [sessionId, setSessionId] = useState(null);
  const [sending, setSending] = useState(false);
  const bottomRef = useRef(null);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  const handleSend = async (e) => {
    e.preventDefault();
    const text = input.trim();
    if (!text) return;

    setMessages((prev) => [...prev, { sender: 'USER', message: text }]);
    setInput('');
    setSending(true);

    try {
      const { data } = await assistantService.chat(text, sessionId);
      setSessionId(data.sessionId);
      setMessages((prev) => [...prev, { sender: 'ASSISTANT', message: data.reply }]);
    } catch {
      setMessages((prev) => [...prev, { sender: 'ASSISTANT', message: 'Sorry, I ran into an error processing that request.' }]);
    } finally {
      setSending(false);
    }
  };

  const suggestions = ['What is ransomware?', 'How do I mitigate SQL injection?', 'Explain MITRE ATT&CK', 'What is a brute force attack?'];

  return (
    <div className="flex flex-col h-[calc(100vh-8rem)] max-w-3xl">
      <h2 className="text-lg font-semibold text-slate-100 mb-3">AI Security Assistant</h2>

      <div className="flex-1 cg-card overflow-y-auto space-y-4 mb-3">
        {messages.map((m, idx) => (
          <div key={idx} className={`flex gap-3 ${m.sender === 'USER' ? 'flex-row-reverse' : ''}`}>
            <div className={`w-8 h-8 rounded-full flex items-center justify-center shrink-0 ${m.sender === 'USER' ? 'bg-cg-info/15 text-cg-info' : 'bg-cg-accent/15 text-cg-accent'}`}>
              {m.sender === 'USER' ? <User size={16} /> : <Bot size={16} />}
            </div>
            <div className={`rounded-lg px-4 py-2 text-sm max-w-[80%] whitespace-pre-wrap ${m.sender === 'USER' ? 'bg-cg-info/10 text-slate-100' : 'bg-cg-surface-alt text-slate-300'}`}>
              {m.message}
            </div>
          </div>
        ))}
        {sending && <p className="text-xs text-slate-500 pl-11">Assistant is typing...</p>}
        <div ref={bottomRef} />
      </div>

      <div className="flex flex-wrap gap-2 mb-2">
        {suggestions.map((s) => (
          <button key={s} onClick={() => setInput(s)} className="text-xs bg-cg-surface-alt border border-cg-border rounded-full px-3 py-1 text-slate-400 hover:text-cg-accent hover:border-cg-accent/40">
            {s}
          </button>
        ))}
      </div>

      <form onSubmit={handleSend} className="flex gap-2">
        <input
          className="cg-input flex-1"
          placeholder="Ask about a threat, CVE, or mitigation..."
          value={input}
          onChange={(e) => setInput(e.target.value)}
        />
        <button type="submit" disabled={sending} aria-label="Send message" className="cg-btn-primary flex items-center gap-1">
          <Send size={16} />
        </button>
      </form>
    </div>
  );
}
