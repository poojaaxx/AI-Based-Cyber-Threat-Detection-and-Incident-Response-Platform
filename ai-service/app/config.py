import os
from pathlib import Path

from dotenv import load_dotenv

load_dotenv()

BASE_DIR = Path(__file__).resolve().parent
DATA_DIR = BASE_DIR / "data"
DATA_DIR.mkdir(exist_ok=True)

MODEL_PATH = DATA_DIR / "threat_model.joblib"
PROTOCOL_ENCODER_PATH = DATA_DIR / "protocol_encoder.joblib"
LABEL_ENCODER_PATH = DATA_DIR / "label_encoder.joblib"
SCALER_PATH = DATA_DIR / "scaler.joblib"

CORS_ALLOWED_ORIGINS = os.getenv("CORS_ALLOWED_ORIGINS", "http://localhost:5173,http://localhost:8080").split(",")

CONFIDENCE_THRESHOLD = float(os.getenv("CONFIDENCE_THRESHOLD", "0.35"))

GROQ_API_KEY = os.getenv("GROQ_API_KEY", "")
