package com.munggle.domain.exception;

import lombok.Getter;

@Getter
public enum ExceptionMessage {

    USER_NOT_FOUND("회원을 찾을 수 없습니다."),
    NICKNAME_ILLEGAL("닉네임이 올바르지 않습니다."),
    PASSWORD_ILLEGAL("비밀번호는 공백이 포함되지 않은 8 ~ 15자리 입니다."),
    SELF_FOLLOW("자기 자신을 팔로우할 수 없습니다."),
    FOLLOW_NOT_FOUND("팔로우를 찾을 수 없습니다."),
    WALK_NOT_FOUND("산책 기록을 찾을 수 없습니다."),
    PASSWORD_NOT_CONFIRM("새 비밀번호와 비밀번호 확인이 일치하지 않습니다."),
    WALK_LOG_NOT_FOUND("산책 로그를 찾을 수 없습니다.");


    private final String message;

    ExceptionMessage(String message) {
        this.message = message;
    }
}
