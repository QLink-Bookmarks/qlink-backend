package com.qlink.common.error

enum class ErrorCode(
    val status: Int,
    val message: String,
) {
    // Internal
    INT_404_0001(404, "등록되지 않은 URL입니다"),
    INT_500_0001(500, "예기치 못한 서버 에러입니다"),

    // Auth
    AUTH_401_0001(401, "인증 정보가 제공되지 않은 요청입니다"),
    AUTH_401_0002(401, "만료되거나 변조된 인증 정보입니다"),
    AUTH_401_0003(401, "인증 형태가 유효하지 않습니다"),
    AUTH_401_0004(401, "예기치 못한 인증 오류입니다"),
}
