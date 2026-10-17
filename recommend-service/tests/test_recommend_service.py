"""
RecommendService 규칙 검증 (외부 서비스 호출은 가짜 클라이언트로 대체)

  - 참여 이력 없음  → 전체 프로그램을 participationCount 내림차순으로 상위 5개
  - 참여 이력 있음  → 최빈 카테고리의 미참여 프로그램 (이미 참여한 프로그램은 exclude 로 전달)
  - 참여 이력 조회 실패는 participation_client 가 "이력 없음" 으로 흡수한다 (아래 client 테스트)
"""
import asyncio
from decimal import Decimal

import httpx
import pytest

from app.client import program_client as program_client_module
from app.config.settings import settings
from app.client import participation_client as participation_client_module
from app.model.schemas import ProgramCategory, ProgramResponse, ParticipationHistoryResponse
from app.service import recommend_service as service_module
from app.service.recommend_service import RecommendService


def program(program_id, category, participation_count=0):
    return ProgramResponse(
        id=program_id, title=f"program-{program_id}", category=category,
        price=Decimal("99000"), officerId=1,
        participationCount=participation_count, status="ACTIVE",
    )


class FakeParticipationClient:
    def __init__(self, active_program_ids):
        self.active_program_ids = active_program_ids

    async def get_participation_history(self, user_id):
        return ParticipationHistoryResponse(userId=user_id, activeProgramIds=self.active_program_ids)


class FakeProgramClient:
    def __init__(self, all_programs, recommended=None):
        self.all_programs = all_programs
        self.recommended = recommended or []
        self.recommend_calls = []

    async def get_all_programs(self):
        return self.all_programs

    async def get_recommend_programs(self, category, exclude_ids):
        self.recommend_calls.append((category, list(exclude_ids)))
        return self.recommended


@pytest.fixture
def install(monkeypatch):
    def _install(active_program_ids, all_programs, recommended=None):
        fake_program = FakeProgramClient(all_programs, recommended)
        monkeypatch.setattr(service_module, "participation_client", FakeParticipationClient(active_program_ids))
        monkeypatch.setattr(service_module, "program_client", fake_program)
        return fake_program
    return _install


def recommend(user_id=1):
    return asyncio.run(RecommendService().get_recommendations(user_id))


def test_이력이_없으면_인기순_상위_5개를_추천한다(install):
    programs = [program(i, ProgramCategory.SAME_DAY, participation_count=i) for i in range(1, 9)]
    install([], programs)

    result = recommend()

    assert result.basedOnCategory is None
    assert [c.id for c in result.recommendedPrograms] == [8, 7, 6, 5, 4]


def test_이력이_있으면_최빈_카테고리로_추천하고_참여한_프로그램은_제외한다(install):
    all_programs = [
        program(1, ProgramCategory.SAME_DAY), program(2, ProgramCategory.SAME_DAY),
        program(3, ProgramCategory.COLD_CHAIN),
    ]
    recommended = [program(10, ProgramCategory.SAME_DAY, 5)]
    fake_program = install([1, 2, 3], all_programs, recommended)

    result = recommend()

    assert result.basedOnCategory == ProgramCategory.SAME_DAY
    assert [c.id for c in result.recommendedPrograms] == [10]
    assert fake_program.recommend_calls == [(ProgramCategory.SAME_DAY, [1, 2, 3])]


def test_추천_결과는_최대_5개로_자른다(install):
    all_programs = [program(1, ProgramCategory.SHARED_STORAGE)]
    recommended = [program(100 + i, ProgramCategory.SHARED_STORAGE, 10 - i) for i in range(8)]
    install([1], all_programs, recommended)

    assert len(recommend().recommendedPrograms) == 5


def test_참여한_프로그램이_목록에서_사라졌으면_신규_사용자처럼_인기순으로_추천한다(install):
    all_programs = [program(1, ProgramCategory.LOCAL_COMMERCE, 3), program(2, ProgramCategory.LOCAL_COMMERCE, 9)]
    install([999], all_programs)  # 999 는 program-service 목록에 없음

    result = recommend()

    assert result.basedOnCategory is None
    assert [c.id for c in result.recommendedPrograms] == [2, 1]


def test_참여_이력_조회가_실패하면_이력_없음으로_처리한다(monkeypatch):
    def failing_transport(request):
        raise httpx.ConnectError("participation-service down")

    real_async_client = httpx.AsyncClient
    monkeypatch.setattr(
        participation_client_module.httpx, "AsyncClient",
        lambda **kw: real_async_client(transport=httpx.MockTransport(failing_transport), **kw),
    )

    history = asyncio.run(
        participation_client_module.ParticipationServiceClient().get_participation_history(7)
    )

    assert history.userId == 7
    assert history.activeProgramIds == []


def test_program_client는_excludeIds를_콤마로_이어_program_service_계약대로_호출한다(monkeypatch):
    seen = {}

    def handler(request):
        seen["token"] = request.headers.get("x-internal-token")
        seen["path"] = request.url.path
        seen["params"] = dict(request.url.params)
        return httpx.Response(200, json=[{
            "id": 3, "title": "t", "category": "SAME_DAY", "price": 99000,
            "officerId": 1, "participationCount": 4, "status": "ACTIVE",
        }])

    real_async_client = httpx.AsyncClient
    monkeypatch.setattr(
        program_client_module.httpx, "AsyncClient",
        lambda **kw: real_async_client(transport=httpx.MockTransport(handler), **kw),
    )

    programs = asyncio.run(
        program_client_module.ProgramServiceClient().get_recommend_programs(ProgramCategory.SAME_DAY, [1, 2])
    )

    assert seen["token"] == settings.internal_api_token
    assert seen["path"] == "/api/programs/internal/recommend"
    assert seen["params"] == {"category": "SAME_DAY", "excludeIds": "1,2"}
    assert [c.id for c in programs] == [3]
