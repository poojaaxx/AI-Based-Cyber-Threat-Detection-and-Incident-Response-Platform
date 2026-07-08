from typing import Optional
from pydantic import BaseModel


class ChatRequest(BaseModel):
    message: str
    context: Optional[str] = None


class ChatReply(BaseModel):
    reply: str
