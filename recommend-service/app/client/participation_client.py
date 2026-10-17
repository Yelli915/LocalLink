import httpx
import logging
from app.config.settings import settings
from app.model.schemas import ParticipationHistoryResponse

logger = logging.getLogger(__name__)


class ParticipationServiceClient:
    """
    Participation Service REST 클라이언트
    - 참여 이력 조회 (ACTIVE 프로그램 ID 목록)
    """

    def __init__(self):
        self.base_url = settings.participation_service_url

    async def get_participation_history(self, user_id: int) -> ParticipationHistoryResponse:
        """
        GET /participations/internal/history/{userId}
        사용자의 참여 중인 프로그램 ID 목록 조회
        """
        url = f"{self.base_url}/api/participations/internal/history/{user_id}"
        try:
            async with httpx.AsyncClient(timeout=5.0) as client:
                response = await client.get(url, headers={"X-Internal-Token": settings.internal_api_token})
                response.raise_for_status()
                data = response.json()
                return ParticipationHistoryResponse(**data)
        except httpx.HTTPError as e:
            logger.error(f"[ParticipationClient] 참여 이력 조회 실패 - userId: {user_id}, error: {e}")
            # 실패 시 빈 이력 반환 (추천 서비스는 비핵심 기능)
            return ParticipationHistoryResponse(userId=user_id, activeProgramIds=[])


participation_client = ParticipationServiceClient()
