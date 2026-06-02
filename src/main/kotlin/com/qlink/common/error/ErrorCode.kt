package com.qlink.common.error

enum class ErrorCode(
    val code: String,
    val status: Int,
    val message: String,
) {
    // Common
    COMMON_BAD_REQUEST("COM_400_0001", 400, "유효하지 않은 요청이에요"),
    COMMON_URL_NOT_FOUND("COM_404_0001", 404, "등록되지 않은 URL이에요"),
    COMMON_INTERNAL_SERVER_ERROR("COM_500_0001", 500, "예기치 못한 서버 에러가 발생했어요"),

    // Auth
    AUTH_NO_CREDENTIALS("AUTH_401_0001", 401, "인증 정보가 제공되지 않은 요청이에요"),
    AUTH_INVALID_CREDENTIALS("AUTH_401_0002", 401, "만료되거나 변조된 인증 정보에요"),
    AUTH_WRONG_CREDENTIALS("AUTH_401_0003", 401, "인증 형태가 유효하지 않아요"),
    AUTH_UNEXPECTED_CREDENTIALS("AUTH_401_0004", 401, "예기치 못한 인증 오류가 발생했어요"),

    // User
    USER_THEME_NOT_SUPPORTED("USER_400_0001", 400, "지원하지 않는 화면 테마에요"),
    USER_ACCENT_NOT_SUPPORTED("USER_400_0002", 400, "지원하지 않는 강조 색상이에요"),
    USER_NOT_FOUND("USER_404_0001", 404, "로그인 사용자를 찾을 수 없어요"),

    // LINK
    LINK_URL_BLANK("LINK_400_0001", 400, "URL에 입력된 값이 없어요"),
    LINK_URL_WRONG_FORMAT("LINK_400_0002", 400, "잘못된 URL이에요"),
    LINK_URL_NOT_HTTP("LINK_400_0003", 400, "HTTP가 아니에요"),
    LINK_URL_WRONG_HOST("LINK_400_0004", 400, "잘못된 호스트에요"),
    LINK_TITLE_BLANK("LINK_400_0005", 400, "링크 제목이 입력되지 않았어요"),
    LINK_TITLE_OVER_MAX("LINK_400_0006", 400, "링크 제목은 최대 300자에요"),

    LINK_DIFFERENT_OWNER("LINK_403_0001", 403, "링크에 대한 권한이 없어요"),

    LINK_NOT_FOUND("LINK_404_0001", 404, "링크를 찾을 수 없어요"),
    LINK_OWNER_NOT_FOUND("LINK_404_0002", 404, "로그인 사용자를 찾을 수 없어요"),
    LINK_FOLDER_NOT_FOUND("LINK_404_0003", 404, "폴더를 찾을 수 없어요"),

    // AI
    AI_PROVIDER_NOT_SUPPORTED("AI_400_0001", 400, "지원하지 않는 AI 제공자에요"),
    AI_API_KEY_INVALID("AI_400_0002", 400, "유효하지 않은 AI API 키에요"),
    AI_MODEL_DIFFERENT_PROVIDER("AI_400_0003", 400, "AI 모델이 제공자에 속하지 않아요"),
    AI_VENDOR_TEMPORARY_UNAVAILABLE("AI_422_0001", 422, "AI 제공자 서비스를 일시적으로 사용할 수 없어요"),
    AI_USER_PROVIDER_NOT_FOUND("AI_404_0001", 404, "AI 제공자 설정을 찾을 수 없어요"),
    AI_MODEL_NOT_FOUND("AI_404_0002", 404, "AI 모델을 찾을 수 없어요"),
    AI_PROVIDER_NOT_FOUND("AI_404_0003", 404, "AI 제공자를 찾을 수 없어요"),
    AI_API_KEY_MISSING("AI_500_0001", 500, "AI API 키가 설정되지 않았어요"),
    AI_EMPTY_RESPONSE("AI_502_0001", 502, "AI 응답이 비어 있어요"),

    // Todo
    TODO_TITLE_BLANK("TODO_400_0001", 400, "할 일 제목이 입력되지 않았어요"),
    TODO_TITLE_OVER_MAX("TODO_400_0002", 400, "할 일 제목은 최대 50자에요"),
    TODO_DIFFERENT_LINK("TODO_400_0003", 400, "링크가 다른 할 일이에요"),
    TODO_DUPLICATE_ID("TODO_400_0004", 400, "할 일 ID가 중복되었어요"),

    TODO_DIFFERENT_OWNER("TODO_403_0001", 403, "할 일에 대한 권한이 없어요"),

    TODO_OWNER_NOT_FOUND("TODO_404_0001", 404, "로그인 사용자를 찾을 수 없어요"),
    TODO_LINK_NOT_FOUND("TODO_404_0002", 404, "링크를 찾을 수 없어요"),
    TODO_NOT_FOUND("TODO_404_0003", 404, "할 일을 찾을 수 없어요"),

    // Folder
    FOLDER_NAME_BLANK("FOLDER_400_0001", 400, "폴더 이름이 입력되지 않았어요"),
    FOLDER_NAME_OVER_MAX("FOLDER_400_0002", 400, "폴더 이름은 최대 100자에요"),
    FOLDER_EMOJI_OVER_MAX("FOLDER_400_0003", 400, "폴더 이모지는 최대 20자에요"),
    FOLDER_EMOJI_INVALID("FOLDER_400_0004", 400, "폴더 이모지 형식이 올바르지 않아요"),
    FOLDER_DIFFERENT_OWNER("FOLDER_403_0001", 403, "폴더에 대한 권한이 없어요"),
    FOLDER_OWNER_NOT_FOUND("FOLDER_404_0001", 404, "로그인 사용자를 찾을 수 없어요"),
    FOLDER_NOT_FOUND("FOLDER_404_0002", 404, "폴더를 찾을 수 없어요"),
    FOLDER_DUPLICATE_NAME("FOLDER_409_0001", 409, "같은 이름의 폴더가 이미 있어요"),
}
