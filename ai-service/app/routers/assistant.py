from fastapi import APIRouter

from app.ml.assistant import generate_reply
from app.schemas.chat import ChatReply, ChatRequest

router = APIRouter(prefix="/api/v1/assistant", tags=["AI Security Assistant"])


@router.post("/chat", response_model=ChatReply)
def chat(request: ChatRequest) -> ChatReply:
    """AI Security Assistant: explains threats/CVEs and recommends mitigations."""
    return ChatReply(reply=generate_reply(request.message, request.context))
