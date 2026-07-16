FROM python:3.11-slim
WORKDIR /app

# Build context is the repo root (deployment/render.yaml), not ai-service/,
# so that the NSL-KDD dataset below can be included - see the COPY dataset
# line and app/ml/kdd_pipeline.py's RAW_DATA_DIR comment for why /dataset.
COPY ai-service/requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY ai-service/app ./app

# kdd_pipeline.py resolves NSL-KDD's location as
# Path(__file__).resolve().parents[3] / "dataset". With this image's layout
# (WORKDIR /app, app copied to ./app), that file lives at
# /app/app/ml/kdd_pipeline.py, so parents[3] is the filesystem root - hence
# /dataset, not ./dataset or /app/dataset.
COPY dataset /dataset

# Train all three models at image build time so first boot is fast and
# deterministic instead of training lazily (slowly) on first request:
# Model A (RandomForest, synthetic data), Model B (attention-LSTM, NSL-KDD -
# train_lstm runs the kdd_pipeline preprocessing step itself if needed), and
# the Q-learning response policy.
RUN python -m app.ml.train_model
RUN python -m app.ml.train_lstm
RUN python -m app.ml.response_policy

EXPOSE 8000
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
