package com.locallink.program.service;

/** 인증은 됐지만 이 작업을 할 권한(역할)이 없는 경우 → 403 */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}
