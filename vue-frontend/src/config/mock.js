/**
 * 학습·시연용 데모 목업 스위치.
 *
 * 백엔드(program-service / participation-service 등)가 비어 있거나 연결되지 않아도
 * 화면 흐름을 시연할 수 있도록, 각 API 래퍼가 "실패 또는 빈 응답"일 때만 목업으로 폴백한다.
 * 실제 데이터가 있으면 목업은 절대 쓰이지 않는다.
 *
 * 기본값은 꺼짐(false)이다. 백엔드 장애가 데모 화면에 가려지지 않도록, 시연할 때만
 * vue-frontend/.env 에서 VITE_ENABLE_MOCK=true 로 켠다.
 * 켜져 있고 실제로 목업 데이터가 화면에 쓰이면 markDemo() 로 전역 "데모 데이터" 배지를 띄운다(App.vue).
 */
import { reactive } from 'vue'

export const demoState = reactive({ active: false })

export function markDemo() {
  demoState.active = true
}

export function isMockEnabled() {
  return String(import.meta.env.VITE_ENABLE_MOCK ?? 'false') === 'true'
}
