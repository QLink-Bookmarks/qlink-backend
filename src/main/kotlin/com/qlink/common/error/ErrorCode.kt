package com.qlink.common.error

enum class ErrorCode(
    val code: String,
    val status: Int,
    val message: String,
) {
    // Common
    COMMON_BAD_REQUEST("COM_400_0001", 400, "유효하지 않은 요청이에요"),
    COMMON_INVALID_SORT_ORDER("COM_400_0002", 400, "지원하지 않는 정렬 기준이에요"),
    COMMON_INVALID_FILTER("COM_400_0003", 400, "요청 필터 값이 올바르지 않아요"),
    COMMON_CURSOR_MALFORMED("COM_400_0004", 400, "스크롤 커서를 해석할 수 없어요"),
    COMMON_CURSOR_ORDER_MISMATCH("COM_400_0005", 400, "커서의 정렬 기준이 요청과 일치하지 않아요"),
    COMMON_CURSOR_FIELD_MISSING("COM_400_0006", 400, "커서에 필요한 값이 없어요"),
    COMMON_URL_NOT_FOUND("COM_404_0001", 404, "등록되지 않은 URL이에요"),
    COMMON_INTERNAL_SERVER_ERROR("COM_500_0001", 500, "예기치 못한 서버 에러가 발생했어요"),

    // Auth
    AUTH_ACCESS_TOKEN_MISSING("AUTH_401_0001", 401, "로그인이 필요해요 (액세스 토큰이 없어요)"),
    AUTH_ACCESS_TOKEN_INVALID("AUTH_401_0002", 401, "액세스 토큰이 만료되었거나 유효하지 않아요"),
    AUTH_WRONG_CREDENTIALS("AUTH_401_0003", 401, "인증 형태가 유효하지 않아요"),
    AUTH_UNEXPECTED_CREDENTIALS("AUTH_401_0004", 401, "예기치 못한 인증 오류가 발생했어요"),
    AUTH_REFRESH_TOKEN_MISSING("AUTH_401_0005", 401, "리프레시 토큰이 없어요"),
    AUTH_REFRESH_TOKEN_INVALID("AUTH_401_0006", 401, "리프레시 토큰이 만료되었거나 유효하지 않아요"),
    AUTH_REFRESH_TOKEN_REUSED("AUTH_401_0007", 401, "세션이 만료되었어요. 다시 로그인해 주세요"),
    AUTH_PROVIDER_NOT_SUPPORTED("AUTH_400_0001", 400, "지원하지 않는 인증 제공자에요"),
    AUTH_PROVIDER_ALREADY_CONNECTED("AUTH_409_0001", 409, "이미 연동된 인증 제공자에요"),
    AUTH_CSRF_TOKEN_INVALID("AUTH_403_0001", 403, "요청 보안 토큰이 유효하지 않아요"),
    AUTH_PROVIDER_COMMUNICATION_FAILED("AUTH_422_0001", 422, "소셜 로그인 제공자와 통신에 실패했어요"),
    AUTH_PROVIDER_TOKEN_INVALID("AUTH_422_0002", 422, "소셜 로그인 토큰이 유효하지 않아요"),

    // User
    USER_THEME_NOT_SUPPORTED("USER_400_0001", 400, "지원하지 않는 화면 테마에요"),
    USER_ACCENT_NOT_SUPPORTED("USER_400_0002", 400, "지원하지 않는 강조 색상이에요"),
    USER_USERNAME_BLANK("USER_400_0003", 400, "사용자 이름이 입력되지 않았어요"),
    USER_USERNAME_UNDER_MIN("USER_400_0004", 400, "사용자 이름은 최소 3자에요"),
    USER_USERNAME_OVER_MAX("USER_400_0005", 400, "사용자 이름은 최대 100자에요"),
    USER_NICKNAME_BLANK("USER_400_0006", 400, "닉네임이 입력되지 않았어요"),
    USER_NICKNAME_OVER_MAX("USER_400_0007", 400, "닉네임은 최대 50자에요"),
    USER_AVATAR_EMOJI_OVER_MAX("USER_400_0008", 400, "프로필 이모지는 최대 20자에요"),
    USER_AVATAR_EMOJI_INVALID("USER_400_0009", 400, "프로필 이모지 형식이 올바르지 않아요"),
    USER_NOT_FOUND("USER_404_0001", 404, "로그인 사용자를 찾을 수 없어요"),
    USER_USERNAME_DUPLICATED("USER_409_0001", 409, "이미 사용 중인 사용자 이름이에요"),

    // LINK
    LINK_URL_BLANK("LINK_400_0001", 400, "URL에 입력된 값이 없어요"),
    LINK_URL_WRONG_FORMAT("LINK_400_0002", 400, "잘못된 URL이에요"),
    LINK_URL_NOT_HTTP("LINK_400_0003", 400, "HTTP가 아니에요"),
    LINK_URL_WRONG_HOST("LINK_400_0004", 400, "잘못된 호스트에요"),
    LINK_TITLE_BLANK("LINK_400_0005", 400, "링크 제목이 입력되지 않았어요"),
    LINK_TITLE_OVER_MAX("LINK_400_0006", 400, "링크 제목은 최대 300자에요"),
    LINK_COPY_FOLDER_MISMATCH("LINK_400_0007", 400, "링크가 요청한 공유 폴더에 속해있지 않아요"),

    LINK_DIFFERENT_OWNER("LINK_403_0001", 403, "링크에 대한 권한이 없어요"),

    LINK_NOT_FOUND("LINK_404_0001", 404, "링크를 찾을 수 없어요"),
    LINK_OWNER_NOT_FOUND("LINK_404_0002", 404, "로그인 사용자를 찾을 수 없어요"),
    LINK_FOLDER_NOT_FOUND("LINK_404_0003", 404, "폴더를 찾을 수 없어요"),
    LINK_SHARED_FOLDER_ACCESS_DENIED("LINK_403_0002", 403, "공유 폴더 링크에 대한 권한이 없어요"),
    LINK_FOLDER_ACCESS_DENIED("LINK_403_0003", 403, "링크 폴더에 대한 권한이 없어요"),
    LINK_SHARE_FOLDER_NOT_FOUND("LINK_404_0004", 404, "공유 폴더를 찾을 수 없어요"),
    LINK_COPY_LINK_FOLDER_NOT_FOUND("LINK_404_0005", 404, "링크의 폴더를 찾을 수 없어요"),
    LINK_TARGET_FOLDER_NOT_FOUND("LINK_404_0006", 404, "대상 폴더를 찾을 수 없어요"),

    LINK_COPY_NOT_SHARED_FOLDER("LINK_422_0001", 422, "공유 폴더가 아니에요"),

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
    TODO_REPEAT_FIELDS_INCOMPLETE("TODO_400_0005", 400, "반복 설정이 완전하지 않아요"),
    TODO_REPEAT_DAYS_EMPTY("TODO_400_0006", 400, "반복 요일이 입력되지 않았어요"),
    TODO_REPEAT_TIME_INVALID("TODO_400_0007", 400, "반복 시간이 올바르지 않아요"),
    TODO_REPEAT_TIMEZONE_INVALID("TODO_400_0008", 400, "반복 시간대가 올바르지 않아요"),
    TODO_REMINDER_AT_INVALID("TODO_400_0009", 400, "알림 시간은 현재 시간보다 이후여야 해요"),

    TODO_DIFFERENT_OWNER("TODO_403_0001", 403, "할 일에 대한 권한이 없어요"),

    TODO_OWNER_NOT_FOUND("TODO_404_0001", 404, "로그인 사용자를 찾을 수 없어요"),
    TODO_LINK_NOT_FOUND("TODO_404_0002", 404, "링크를 찾을 수 없어요"),
    TODO_NOT_FOUND("TODO_404_0003", 404, "할 일을 찾을 수 없어요"),

    // Notification
    NOTIFICATION_TITLE_BLANK("NOTI_400_0001", 400, "알림 제목이 입력되지 않았어요"),
    NOTIFICATION_TITLE_OVER_MAX("NOTI_400_0002", 400, "알림 제목은 최대 50자에요"),
    NOTIFICATION_MESSAGE_OVER_MAX("NOTI_400_0003", 400, "알림 메시지는 최대 200자에요"),
    NOTIFICATION_COUNT_INVALID("NOTI_400_0004", 400, "알림 처리 건수가 올바르지 않아요"),
    NOTIFICATION_NOT_FOUND("NOTI_404_0001", 404, "알림을 찾을 수 없어요"),
    NOTIFICATION_NOT_FIRED("NOTI_422_0001", 422, "아직 발송되지 않은 알림이에요"),

    // Device
    DEVICE_PLATFORM_NOT_SUPPORTED("DEVICE_400_0001", 400, "지원하지 않는 디바이스 플랫폼이에요"),
    DEVICE_TOKEN_BLANK("DEVICE_400_0002", 400, "디바이스 토큰이 입력되지 않았어요"),

    // Push
    PUSH_PLATFORM_NOT_SUPPORTED("PUSH_400_0001", 400, "지원하지 않는 푸시 플랫폼이에요"),

    // Folder
    FOLDER_NAME_BLANK("FOLDER_400_0001", 400, "폴더 이름이 입력되지 않았어요"),
    FOLDER_NAME_OVER_MAX("FOLDER_400_0002", 400, "폴더 이름은 최대 100자에요"),
    FOLDER_EMOJI_OVER_MAX("FOLDER_400_0003", 400, "폴더 이모지는 최대 20자에요"),
    FOLDER_EMOJI_INVALID("FOLDER_400_0004", 400, "폴더 이모지 형식이 올바르지 않아요"),
    FOLDER_DIFFERENT_OWNER("FOLDER_403_0001", 403, "폴더에 대한 권한이 없어요"),
    FOLDER_OWNER_NOT_FOUND("FOLDER_404_0001", 404, "로그인 사용자를 찾을 수 없어요"),
    FOLDER_NOT_FOUND("FOLDER_404_0002", 404, "폴더를 찾을 수 없어요"),
    FOLDER_DUPLICATE_NAME("FOLDER_409_0001", 409, "같은 이름의 폴더가 이미 있어요"),
    FOLDER_INVITATION_INVALID("FOLDER_400_0005", 400, "폴더 초대 정보가 올바르지 않아요"),
    FOLDER_NOT_SHARED("FOLDER_422_0001", 422, "공유 폴더가 아니에요"),
    FOLDER_INVITATION_EXPIRED("FOLDER_422_0002", 422, "폴더 초대가 만료되었어요"),

    FOLDER_MEMBER_OWNER_NOT_FOUND("FOLDER_MEMBER_404_0001", 404, "로그인 사용자를 찾을 수 없어요"),
    FOLDER_MEMBER_FOLDER_NOT_FOUND("FOLDER_MEMBER_404_0002", 404, "폴더를 찾을 수 없어요"),
    FOLDER_MEMBER_ACCESS_DENIED("FOLDER_MEMBER_403_0001", 403, "공유 폴더 멤버에 대한 권한이 없어요"),
    FOLDER_MEMBER_NOT_SHARED_FOLDER("FOLDER_MEMBER_422_0001", 422, "공유 폴더가 아니에요"),

    // Image
    IMAGE_FILE_REQUIRED("IMAGE_400_0001", 400, "이미지 파일이 필요해요"),
    IMAGE_INVALID_FORMAT("IMAGE_400_0002", 400, "지원하지 않는 이미지 형식이에요"),
    IMAGE_FILE_TOO_LARGE("IMAGE_400_0003", 400, "이미지 파일 크기가 너무 커요"),
    IMAGE_OWNER_NOT_FOUND("IMAGE_404_0001", 404, "로그인 사용자를 찾을 수 없어요"),
    IMAGE_UPLOAD_FAILED("IMAGE_500_0001", 500, "이미지 업로드에 실패했어요"),
}
