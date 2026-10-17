import logging
from fastapi import APIRouter, Depends
from app.config.security import verify_token
from app.model.schemas import RecommendResponse
from app.service.recommend_service import recommend_service

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/api/recommend", tags=["recommend"])


# 고정 경로(/health)는 반드시 /{user_id} 보다 먼저 선언한다 - 아니면 'health' 가 int 로 파싱되려다 422 가 난다
@router.get("/health", include_in_schema=False)
async def health_check():
    return {"status": "UP", "service": "recommend-service"}


@router.get("/{user_id}", response_model=RecommendResponse)
async def get_recommendations(
    user_id: int,
    token_payload: dict = Depends(verify_token)
):
    """
    GET /recommend/{userId} - 사용자 기반 프로그램 추천

    추천 규칙:
    - 참여 이력 있음: 최빈 카테고리 기반 미참여 프로그램 추천 (참여자 수 기준 정렬)
    - 참여 이력 없음: 전체 인기 프로그램 추천
    """
    logger.info(f"[Router] 추천 요청 - userId: {user_id}")
    return await recommend_service.get_recommendations(user_id)
