"""
Attention-LSTM temporal detector ("Model B") - a genuine (if intentionally
small-scale) implementation of the paper's core idea: an LSTM encodes a
sequence of events, and a self-attention layer learns to weigh which
timesteps in that sequence matter most for the final classification, instead
of just taking the LSTM's last hidden state.

Simplification vs. the base paper: this is a single-head additive attention
over one LSTM's hidden states, not the paper's full attention-LSTM
architecture (e.g. multi-head attention, deeper stacked encoders). It is a
real, trainable, effective attention mechanism - just a scoped-down one, to
keep this an honest extension rather than an over-claimed reproduction.
"""
import torch
import torch.nn as nn


class SelfAttention(nn.Module):
    """Additive (Bahdanau-style) self-attention over an LSTM's per-timestep
    hidden states. Produces one attention weight per timestep (how much that
    event in the window contributed to the final decision) and a single
    context vector = the attention-weighted sum of hidden states."""

    def __init__(self, hidden_size: int):
        super().__init__()
        self.attn_score = nn.Sequential(
            nn.Linear(hidden_size, hidden_size),
            nn.Tanh(),
            nn.Linear(hidden_size, 1),
        )

    def forward(self, hidden_states: torch.Tensor):
        # hidden_states: (batch, seq_len, hidden_size)
        scores = self.attn_score(hidden_states).squeeze(-1)  # (batch, seq_len)
        weights = torch.softmax(scores, dim=1)  # (batch, seq_len)
        context = torch.bmm(weights.unsqueeze(1), hidden_states).squeeze(1)  # (batch, hidden_size)
        return context, weights


class LSTMAttentionClassifier(nn.Module):
    def __init__(self, input_size: int, hidden_size: int = 64, num_classes: int = 5, num_layers: int = 1, dropout: float = 0.2):
        super().__init__()
        self.lstm = nn.LSTM(
            input_size=input_size,
            hidden_size=hidden_size,
            num_layers=num_layers,
            batch_first=True,
        )
        self.attention = SelfAttention(hidden_size)
        self.dropout = nn.Dropout(dropout)
        self.classifier = nn.Linear(hidden_size, num_classes)

    def forward(self, x: torch.Tensor):
        # x: (batch, window, input_size)
        hidden_states, _ = self.lstm(x)  # (batch, window, hidden_size)
        context, attn_weights = self.attention(hidden_states)
        out = self.classifier(self.dropout(context))
        return out, attn_weights
