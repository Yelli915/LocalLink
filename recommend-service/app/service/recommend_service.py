import logging
from collections import Counter
from typing import List, Optional

from app.client.program_client import program_client
from app.client.participation_client import participation_client
from app.model.schemas import ProgramCategory, ProgramResponse, RecommendResponse

logger = logging.getLogger(__name__)


class RecommendService:
    """
    규칙 기반 프로그램 추천 서비스

    추천 규칙:
    1. 사용자의 참여 중인 프로그램 카테고리 분석
    2. 가장 많이 참여한 카테고리 선택 (최빈 카테고리)
    3. 해당 카테고리에서 미참여 프로그램 조회
    4. 참여자 수 기준 내림차순 정렬하여 반환
    5. 참여 이력 없으면 전체 프로그램 중 인기순 반환
    """

    MAX_RECOMMEND_COUNT = 5  # 최대 추천 프로그램 수

    async def get_recommendations(self, user_id: int) -> RecommendResponse:
        logger.info(f"[RecommendService] 추천 시작 - userId: {user_id}")

        # 1. 참여 이력 조회
        history = await participation_client.get_participation_history(user_id)
        active_program_ids = history.activeProgramIds

        # 2. 참여 이력 없는 신규 사용자 처리
        if not active_program_ids:
            return await self._recommend_for_new_user(user_id)

        # 3. 참여한 프로그램의 카테고리 분석 → 최빈 카테고리 선택
        dominant_category = await self._find_dominant_category(active_program_ids)
        if not dominant_category:
            return await self._recommend_for_new_user(user_id)

        # 4. 최빈 카테고리 기반 미참여 프로그램 조회
        recommended = await program_client.get_recommend_programs(
            category=dominant_category,
            exclude_ids=active_program_ids
        )

        # 5. 최대 추천 수 제한
        recommended = recommended[:self.MAX_RECOMMEND_COUNT]

        logger.info(f"[RecommendService] 추천 완료 - userId: {user_id}, "
                    f"category: {dominant_category}, count: {len(recommended)}")

        return RecommendResponse(
            userId=user_id,
            recommendedPrograms=recommended,
            basedOnCategory=dominant_category,
            message=f"{dominant_category.value} 카테고리 기반 추천 프로그램입니다"
        )

    async def _find_dominant_category(
        self, program_ids: List[int]
    ) -> Optional[ProgramCategory]:
        """
        참여한 프로그램들의 카테고리 분석 → 최빈 카테고리 반환
        Program Service에서 각 프로그램 정보를 조회하여 카테고리 집계
        """
        all_programs = await program_client.get_all_programs()
        program_map = {c.id: c for c in all_programs}

        categories = [
            program_map[cid].category
            for cid in program_ids
            if cid in program_map
        ]

        if not categories:
            return None

        # Counter로 최빈 카테고리 선택
        most_common = Counter(categories).most_common(1)
        return most_common[0][0] if most_common else None

    async def _recommend_for_new_user(self, user_id: int) -> RecommendResponse:
        """
        신규 사용자: 참여자 수 기준 전체 인기 프로그램 추천
        """
        logger.info(f"[RecommendService] 신규 사용자 추천 - userId: {user_id}")

        all_programs = await program_client.get_all_programs()
        popular = sorted(
            all_programs,
            key=lambda c: c.participationCount,
            reverse=True
        )[:self.MAX_RECOMMEND_COUNT]

        return RecommendResponse(
            userId=user_id,
            recommendedPrograms=popular,
            basedOnCategory=None,
            message="인기 프로그램 추천입니다"
        )


recommend_service = RecommendService()
