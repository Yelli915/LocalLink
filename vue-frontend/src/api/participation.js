import api from './index.js'
import {
  isMockEnabled,
  readMockParticipations,
  addMockParticipation,
} from './mockParticipations.js'
import { markDemo } from '@/config/mock.js'

/**
 * participation-service 계약은 그대로 사용한다.
 * 다만 백엔드가 없거나(연결 실패) 참여신청이 하나도 없을 때는,
 * 학습·시연용 데모를 위해 목업 참여신청으로 폴백한다(mockParticipations.js 참고).
 * 기본은 꺼짐이며 VITE_ENABLE_MOCK=true 로 켠다(켜지면 "데모 데이터" 배지 표시).
 */
export const participationApi = {
  async getMyParticipations() {
    try {
      const res = await api.get('/api/participations/my')
      const payload = res.data
      const list = Array.isArray(payload?.data)
        ? payload.data
        : Array.isArray(payload)
          ? payload
          : []

      if (list.length === 0 && isMockEnabled()) {
        console.info('[participationApi] 참여신청 없음 → 데모 목업 데이터로 표시')
        markDemo()
        return { data: { data: readMockParticipations(), mock: true } }
      }
      return res
    } catch (error) {
      if (isMockEnabled()) {
        console.warn('[participationApi] GET /api/participations/my 실패 → 데모 목업 데이터로 표시', error)
        markDemo()
        return { data: { data: readMockParticipations(), mock: true } }
      }
      throw error
    }
  },

  /**
   * 참여 신청 = 백엔드 정산 사가 트리거.
   * POST /api/participations 한 번이 PENDING participation 생성 + settlement-service 동기 호출 +
   * settlement.completed Kafka 발행까지 수행한다. PENDING→ACTIVE 전환은 이후 비동기.
   *
   * @param {number|string} programId
   * @param {object} [programSnapshot] 표시용 program 정보(title/category/price/officerName)
   * @param {object} [options]
   * @param {boolean} [options.allowMockFallback=true] 백엔드 호출 실패 시 목업 신청으로 폴백할지.
   *   실제 흐름(ProgramApplyView)에서 카탈로그가 실데이터면 false 로 넘겨 진짜 에러를 노출한다.
   */
  async participate(programId, programSnapshot = {}, options = {}) {
    const { allowMockFallback = true } = options
    try {
      return await api.post('/api/participations', { programId })
    } catch (error) {
      if (allowMockFallback && isMockEnabled()) {
        console.warn('[participationApi] POST /api/participations 실패 → 데모 목업 신청으로 처리', error)
        const item = addMockParticipation(programId, programSnapshot)
        markDemo()
        return { data: { data: item, mock: true }, status: 201 }
      }
      throw error
    }
  },

  // 참고: participation-service에는 신청 취소(DELETE) 엔드포인트가 없다. 취소 기능은 백엔드 추가가 필요.
  getRecommendations(userId) {
    return api.get(`/api/recommend/${userId}`)
  },
}
