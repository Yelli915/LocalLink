import httpx
import logging
from typing import List
from app.config.settings import settings
from app.model.schemas import ProgramResponse, ProgramCategory

logger = logging.getLogger(__name__)

# /internal/** 는 서비스 간 공유 토큰이 필요하다 (공개 경로인 GET /api/programs 에는 붙이지 않는다)
INTERNAL_HEADERS = {"X-Internal-Token": settings.internal_api_token}


class ProgramServiceClient:
    """
    Program Service REST 클라이언트
    - 카테고리별 미참여 프로그램 목록 조회
    """

    def __init__(self):
        self.base_url = settings.program_service_url

    async def get_recommend_programs(
        self,
        category: ProgramCategory,
        exclude_ids: List[int]
    ) -> List[ProgramResponse]:
        """
        GET /programs/internal/recommend
        카테고리 기반 미참여 프로그램 목록 조회 (참여자 수 기준 정렬)
        """
        url = f"{self.base_url}/api/programs/internal/recommend"
        params = {"category": category.value}
        if exclude_ids:
            params["excludeIds"] = ",".join(str(i) for i in exclude_ids)

        try:
            async with httpx.AsyncClient(timeout=5.0) as client:
                response = await client.get(url, params=params, headers=INTERNAL_HEADERS)
                response.raise_for_status()
                return [ProgramResponse(**c) for c in response.json()]
        except httpx.HTTPError as e:
            logger.error(f"[ProgramClient] 추천 프로그램 조회 실패 - category: {category}, error: {e}")
            return []

    async def get_all_programs(self) -> List[ProgramResponse]:
        """
        GET /programs - 전체 프로그램 목록 조회
        참여 이력이 없는 신규 사용자 추천용
        """
        url = f"{self.base_url}/api/programs"
        try:
            async with httpx.AsyncClient(timeout=5.0) as client:
                response = await client.get(url)
                response.raise_for_status()
                data = response.json()
                programs = data.get("data", [])
                return [ProgramResponse(**c) for c in programs]
        except httpx.HTTPError as e:
            logger.error(f"[ProgramClient] 전체 프로그램 조회 실패 - error: {e}")
            return []


program_client = ProgramServiceClient()
