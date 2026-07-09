import logging

# Imported first, deliberately, before anything that imports shap (which pulls in
# numba/llvmlite). On Windows, loading numba/llvmlite's native runtime before
# torch's own DLLs (c10.dll et al.) causes a DLL-init failure (WinError 1114);
# importing torch first avoids it. Keep this above the app.routers import below.
import torch  # noqa: F401

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.config import CORS_ALLOWED_ORIGINS
from app.routers import assistant, health, policy, predict

logging.basicConfig(level=logging.INFO)

app = FastAPI(
    title="CyberGuard AI Service",
    description="AI-Based Cyber Threat Detection and Incident Response Platform - "
                "Machine Learning threat classification + AI Security Assistant microservice.",
    version="1.0.0",
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=CORS_ALLOWED_ORIGINS,
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(health.router)
app.include_router(predict.router)
app.include_router(assistant.router)
app.include_router(policy.router)


@app.get("/")
def root():
    return {
        "service": "CyberGuard AI Service",
        "status": "running",
        "docs": "/docs",
    }
