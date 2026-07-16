FROM python:3.11-slim
WORKDIR /app

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY app ./app

# The NSL-KDD dataset is duplicated here (ai-service/dataset/) rather than
# read from the repo-root dataset/ folder, because this image's Docker build
# context is scoped to ai-service/ (deployment/render.yaml) and Render does
# not reliably re-sync an existing service's context/root-directory setting
# from render.yaml on push - keeping everything the build needs inside
# ai-service/ avoids depending on that.
#
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
