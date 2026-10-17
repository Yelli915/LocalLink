package com.locallink.participation.service;

/**
 * 정산 요청을 보냈지만 결과를 알 수 없는 경우 (응답 타임아웃, settlement-service 5xx).
 * "정산이 실패했다" 와 구분해야 한다 - 정산은 실제로 완료됐을 수 있기 때문이다.
 */
public class SettlementOutcomeUnknownException extends RuntimeException {

    public SettlementOutcomeUnknownException(String message, Throwable cause) {
        super(message, cause);
    }
}
