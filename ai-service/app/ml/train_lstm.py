"""
Trains the attention-LSTM temporal detector ("Model B") on the windowed
NSL-KDD sequences produced by app/ml/kdd_pipeline.py, reports
accuracy/precision/recall/F1, and saves the trained weights + model config to
app/data/lstm_attention_model.pt.

Run with:  python -m app.ml.train_lstm
(run app.ml.kdd_pipeline first if app/data/kdd_processed/ doesn't exist yet)
"""
import time

import numpy as np
import torch
import torch.nn as nn
from sklearn.metrics import accuracy_score, precision_recall_fscore_support
from torch.utils.data import DataLoader, TensorDataset

from app.config import DATA_DIR
from app.ml.kdd_pipeline import KDD_PROCESSED_DIR, build_and_save
from app.ml.lstm_attention_model import LSTMAttentionClassifier

LSTM_MODEL_PATH = DATA_DIR / "lstm_attention_model.pt"

HIDDEN_SIZE = 64
NUM_EPOCHS = 20
BATCH_SIZE = 64
LEARNING_RATE = 1e-3
RANDOM_SEED = 42


def _load_processed_data():
    if not (KDD_PROCESSED_DIR / "X_train.npy").exists():
        print("Processed NSL-KDD tensors not found - running app.ml.kdd_pipeline first...")
        build_and_save()

    X_train = np.load(KDD_PROCESSED_DIR / "X_train.npy")
    y_train = np.load(KDD_PROCESSED_DIR / "y_train.npy")
    X_test = np.load(KDD_PROCESSED_DIR / "X_test.npy")
    y_test = np.load(KDD_PROCESSED_DIR / "y_test.npy")
    return X_train, y_train, X_test, y_test


def train_and_save():
    torch.manual_seed(RANDOM_SEED)

    X_train, y_train, X_test, y_test = _load_processed_data()
    input_size = X_train.shape[2]
    num_classes = int(max(y_train.max(), y_test.max()) + 1)
    print(f"Train sequences: {X_train.shape}, Test sequences: {X_test.shape}, num_classes={num_classes}")

    train_ds = TensorDataset(torch.from_numpy(X_train), torch.from_numpy(y_train).long())
    train_loader = DataLoader(train_ds, batch_size=BATCH_SIZE, shuffle=True)

    model = LSTMAttentionClassifier(input_size=input_size, hidden_size=HIDDEN_SIZE, num_classes=num_classes)

    # Rare classes (U2R has ~5 train sequences) would otherwise be drowned out by
    # cross-entropy's implicit uniform weighting - use inverse-frequency class
    # weights so the loss actually pushes the model to learn them too.
    class_counts = np.bincount(y_train, minlength=num_classes).astype(np.float32)
    class_weights = torch.tensor(1.0 / np.clip(class_counts, 1, None), dtype=torch.float32)
    class_weights = class_weights * (num_classes / class_weights.sum())

    criterion = nn.CrossEntropyLoss(weight=class_weights)
    optimizer = torch.optim.Adam(model.parameters(), lr=LEARNING_RATE)

    print("\nTraining attention-LSTM...")
    model.train()
    epoch_losses = []
    for epoch in range(1, NUM_EPOCHS + 1):
        total_loss = 0.0
        for batch_X, batch_y in train_loader:
            optimizer.zero_grad()
            logits, _ = model(batch_X)
            loss = criterion(logits, batch_y)
            loss.backward()
            optimizer.step()
            total_loss += loss.item() * batch_X.size(0)
        avg_loss = total_loss / len(train_ds)
        epoch_losses.append(avg_loss)
        print(f"Epoch {epoch:2d}/{NUM_EPOCHS} - loss: {avg_loss:.4f}")

    assert not any(np.isnan(epoch_losses)), "Training loss diverged to NaN"
    assert epoch_losses[-1] < epoch_losses[0], "Training loss did not decrease"

    model.eval()
    with torch.no_grad():
        start = time.perf_counter()
        logits, _ = model(torch.from_numpy(X_test))
        inference_time = time.perf_counter() - start
        y_pred = logits.argmax(dim=1).numpy()

    acc = accuracy_score(y_test, y_pred)
    precision, recall, f1, _ = precision_recall_fscore_support(
        y_test, y_pred, average="macro", zero_division=0
    )
    print(f"\nTest accuracy:  {acc:.4f}")
    print(f"Test precision (macro): {precision:.4f}")
    print(f"Test recall (macro):    {recall:.4f}")
    print(f"Test F1 (macro):        {f1:.4f}")
    print(f"Inference time for {len(X_test)} sequences: {inference_time * 1000:.1f} ms "
          f"({inference_time / len(X_test) * 1000:.3f} ms/sequence)")

    random_baseline_f1 = 1.0 / num_classes
    assert f1 > 0.5, f"Test F1 ({f1:.4f}) did not clear the 0.5 pass threshold"
    print(f"\n(Random-guess baseline F1 for {num_classes}-class problem ~= {random_baseline_f1:.3f}; "
          f"required > 0.5; achieved {f1:.4f}.)")

    torch.save(
        {
            "state_dict": model.state_dict(),
            "input_size": input_size,
            "hidden_size": HIDDEN_SIZE,
            "num_classes": num_classes,
        },
        LSTM_MODEL_PATH,
    )
    print(f"\nSaved attention-LSTM model to {LSTM_MODEL_PATH}")
    return {"accuracy": acc, "precision": precision, "recall": recall, "f1": f1}


if __name__ == "__main__":
    train_and_save()
