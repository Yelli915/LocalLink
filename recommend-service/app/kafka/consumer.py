import json
import logging
import threading
from kafka import KafkaConsumer
from app.config.settings import settings

logger = logging.getLogger(__name__)


class ParticipationCompletedConsumer:
    """
    Kafka Consumer: participation.completed 이벤트 수신
    - Participation Service가 참여 활성화 후 발행
    - 실시간 추천 캐시 갱신 트리거 (현재는 로그 처리까지만)
    """

    def __init__(self):
        self.topic = settings.kafka_topic_participation_completed
        self.consumer = None
        self._running = False

    def start(self):
        """별도 스레드로 Kafka Consumer 시작"""
        self._running = True
        thread = threading.Thread(target=self._consume, daemon=True)
        thread.start()
        logger.info(f"[KafkaConsumer] 시작 - topic: {self.topic}")

    def stop(self):
        self._running = False
        if self.consumer:
            self.consumer.close()

    def _consume(self):
        try:
            self.consumer = KafkaConsumer(
                self.topic,
                bootstrap_servers=settings.kafka_bootstrap_servers,
                group_id=settings.kafka_consumer_group_id,
                auto_offset_reset="earliest",
                enable_auto_commit=True,
                value_deserializer=lambda m: json.loads(m.decode("utf-8")),
                consumer_timeout_ms=1000,
            )

            while self._running:
                for message in self.consumer:
                    if not self._running:
                        break
                    self._handle_message(message.value)

        except Exception as e:
            logger.error(f"[KafkaConsumer] 오류 발생: {e}")
        finally:
            if self.consumer:
                self.consumer.close()

    def _handle_message(self, event: dict):
        """
        participation.completed 이벤트 처리
        - participationId, userId, programId 추출
        - 추천 캐시 갱신 트리거 (현재는 로그로 대체)
        """
        try:
            participation_id = event.get("participationId")
            user_id = event.get("userId")
            program_id = event.get("programId")

            logger.info(
                f"[KafkaConsumer] participation.completed 수신 - "
                f"participationId: {participation_id}, userId: {user_id}, programId: {program_id}"
            )

            # 확장 포인트: 여기서 캐시 갱신 또는 추천 재계산 로직 추가 가능
            # 예: recommend_cache.invalidate(user_id)

        except Exception as e:
            logger.error(f"[KafkaConsumer] 메시지 처리 실패: {e}, event: {event}")


participation_consumer = ParticipationCompletedConsumer()
