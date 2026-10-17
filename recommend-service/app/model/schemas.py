from pydantic import BaseModel
from typing import List, Optional
from enum import Enum
from decimal import Decimal
from datetime import datetime


class ProgramCategory(str, Enum):
    SAME_DAY = "SAME_DAY"
    REGULAR_BUNDLE = "REGULAR_BUNDLE"
    COLD_CHAIN = "COLD_CHAIN"
    TRADITIONAL_MARKET = "TRADITIONAL_MARKET"
    LOCAL_COMMERCE = "LOCAL_COMMERCE"
    SAFE_DELIVERY = "SAFE_DELIVERY"
    SHARED_STORAGE = "SHARED_STORAGE"
    OTHER = "OTHER"


class ProgramResponse(BaseModel):
    id: int
    title: str
    description: Optional[str] = None
    category: ProgramCategory
    price: Decimal
    officerId: int
    participationCount: int
    status: str
    createdAt: Optional[datetime] = None


class ParticipationHistoryResponse(BaseModel):
    userId: int
    activeProgramIds: List[int]


class RecommendResponse(BaseModel):
    userId: int
    recommendedPrograms: List[ProgramResponse]
    basedOnCategory: Optional[ProgramCategory] = None
    message: str


class ApiResponse(BaseModel):
    success: bool
    message: str
    data: Optional[dict] = None
