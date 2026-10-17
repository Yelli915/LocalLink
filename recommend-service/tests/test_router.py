"""라우터 계약: 고정 경로 /health 는 /{user_id} 에 가려지지 않아야 한다."""
from fastapi import FastAPI
from fastapi.testclient import TestClient

from app.config.security import verify_token
from app.model.schemas import RecommendResponse
from app.router import recommend_router


def make_client(monkeypatch):
    async def fake_recommendations(user_id):
        return RecommendResponse(userId=user_id, recommendedPrograms=[], basedOnCategory=None, message="ok")

    monkeypatch.setattr(recommend_router.recommend_service, "get_recommendations", fake_recommendations)
    app = FastAPI()
    app.include_router(recommend_router.router)
    app.dependency_overrides[verify_token] = lambda: {"sub": "1"}
    return TestClient(app)


def test_health는_토큰_없이_UP을_반환한다(monkeypatch):
    client = make_client(monkeypatch)
    # 토큰 의존성을 걷어내도(=인증 헤더 없이) health 는 통과해야 한다
    response = client.get("/api/recommend/health")

    assert response.status_code == 200
    assert response.json() == {"status": "UP", "service": "recommend-service"}


def test_숫자_경로는_추천_API로_간다(monkeypatch):
    client = make_client(monkeypatch)

    response = client.get("/api/recommend/7")

    assert response.status_code == 200
    assert response.json()["userId"] == 7
