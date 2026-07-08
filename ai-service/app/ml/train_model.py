"""
Trains the RandomForest threat classification model on a synthetic,
class-conditional network-event dataset and persists the model plus the
protocol/label encoders and feature scaler to app/data/*.joblib.

Run with:  python -m app.ml.train_model
"""
import joblib
import numpy as np
from sklearn.ensemble import RandomForestClassifier
from sklearn.metrics import accuracy_score, classification_report
from sklearn.model_selection import train_test_split
from sklearn.preprocessing import LabelEncoder, StandardScaler

from app.config import LABEL_ENCODER_PATH, MODEL_PATH, PROTOCOL_ENCODER_PATH, SCALER_PATH
from app.ml.constants import PROTOCOLS
from app.ml.dataset import generate_dataset
from app.ml.feature_engineering import dataframe_to_features


def train_and_save():
    print("Generating synthetic training dataset...")
    df = generate_dataset(samples_per_class=1200)
    print(f"Dataset shape: {df.shape}")

    protocol_encoder = LabelEncoder()
    protocol_encoder.fit(PROTOCOLS + list(df["protocol"].str.upper().unique()))

    label_encoder = LabelEncoder()
    y = label_encoder.fit_transform(df["threatType"])

    X = dataframe_to_features(df, protocol_encoder)

    scaler = StandardScaler()
    X_scaled = scaler.fit_transform(X)

    X_train, X_test, y_train, y_test = train_test_split(
        X_scaled, y, test_size=0.2, random_state=42, stratify=y
    )

    print("Training RandomForestClassifier...")
    model = RandomForestClassifier(
        n_estimators=300,
        max_depth=18,
        min_samples_split=4,
        random_state=42,
        n_jobs=-1,
        class_weight="balanced",
    )
    model.fit(X_train, y_train)

    y_pred = model.predict(X_test)
    acc = accuracy_score(y_test, y_pred)
    print(f"\nTest accuracy: {acc:.4f}\n")
    print(classification_report(y_test, y_pred, target_names=label_encoder.classes_))

    joblib.dump(model, MODEL_PATH)
    joblib.dump(protocol_encoder, PROTOCOL_ENCODER_PATH)
    joblib.dump(label_encoder, LABEL_ENCODER_PATH)
    joblib.dump(scaler, SCALER_PATH)
    print(f"\nSaved model artifacts to {MODEL_PATH.parent}")


if __name__ == "__main__":
    train_and_save()
