package com.locallink.program.service;

/** 권한 확인을 위해 user-service 를 조회하지 못한 경우 → 503 (확인하지 못했으므로 허용하지 않는다) */
public class UserServiceUnavailableException extends RuntimeException {

    public UserServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
