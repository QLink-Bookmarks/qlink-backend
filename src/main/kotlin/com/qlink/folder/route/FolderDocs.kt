package com.qlink.folder.route

import com.qlink.common.docs.authErrorResponse
import com.qlink.common.docs.examples
import com.qlink.common.error.ErrorCode
import com.qlink.common.response.ApiResponse
import com.qlink.common.response.EmptySuccessResponse
import com.qlink.common.response.ErrorDetail
import com.qlink.common.scroll.ScrollResponse
import com.qlink.folder.dto.AcceptFolderInvitationRequest
import com.qlink.folder.dto.AcceptFolderInvitationResponse
import com.qlink.folder.dto.CreateFolderInvitationRequest
import com.qlink.folder.dto.CreateFolderInvitationResponse
import com.qlink.folder.dto.CreateFolderRequest
import com.qlink.folder.dto.CreateFolderResponse
import com.qlink.folder.dto.GetFolderMembersResponse
import com.qlink.folder.dto.GetFoldersContentResponse
import com.qlink.folder.dto.TransferFolderOwnerRequest
import com.qlink.folder.dto.UpdateFolderRequest
import com.qlink.folder.dto.UpdateFolderResponse
import io.github.smiley4.ktoropenapi.config.RouteConfig
import io.ktor.http.HttpStatusCode

internal fun getFoldersDocs(): RouteConfig.() -> Unit =
    {
        summary = "폴더 목록 조회 API"
        request {
            queryParameter<String?>("query") { description = "검색어" }
            queryParameter<String>("order") {
                description = "정렬 기준 (latest / earliest: 생성 시각(createdAt) 기준, 같으면 id / laxico / similar), 기본값: latest"
            }
            queryParameter<String?>("cursor") { description = "페이지네이션 커서" }
            queryParameter<Int>("size") { description = "페이지 크기, 기본값: 15" }
        }
        response {
            code(HttpStatusCode.OK) {
                description = "폴더 목록 조회 성공"
                body<ApiResponse<ScrollResponse<GetFoldersContentResponse>>>()
            }
            code(HttpStatusCode.BadRequest) {
                description = "폴더 목록 조회 요청이 올바르지 않음"
                body<ApiResponse<ErrorDetail>> {
                    examples(
                        ErrorCode.COMMON_INVALID_SORT_ORDER,
                        ErrorCode.COMMON_CURSOR_MALFORMED,
                        ErrorCode.COMMON_CURSOR_ORDER_MISMATCH,
                        ErrorCode.COMMON_CURSOR_FIELD_MISSING,
                    )
                }
            }
            authErrorResponse()
            code(HttpStatusCode.NotFound) {
                description = "폴더 목록 조회에 필요한 리소스 조회 실패"
                body<ApiResponse<ErrorDetail>> {
                    examples(ErrorCode.FOLDER_OWNER_NOT_FOUND)
                }
            }
        }
    }

internal fun createFolderDocs(): RouteConfig.() -> Unit =
    {
        summary = "폴더 생성 API"
        request { body<CreateFolderRequest>() }
        response {
            code(HttpStatusCode.Created) {
                description = "폴더 생성 성공"
                body<ApiResponse<CreateFolderResponse>>()
            }
            code(HttpStatusCode.BadRequest) {
                description = "폴더 생성 요청 유효성 검증 실패"
                body<ApiResponse<ErrorDetail>> {
                    examples(
                        ErrorCode.FOLDER_NAME_BLANK,
                        ErrorCode.FOLDER_NAME_OVER_MAX,
                        ErrorCode.FOLDER_EMOJI_OVER_MAX,
                        ErrorCode.FOLDER_EMOJI_INVALID,
                    )
                }
            }
            authErrorResponse()
            code(HttpStatusCode.NotFound) {
                description = "폴더 생성에 필요한 리소스 조회 실패"
                body<ApiResponse<ErrorDetail>> {
                    examples(ErrorCode.FOLDER_OWNER_NOT_FOUND)
                }
            }
            code(HttpStatusCode.Conflict) {
                description = "같은 이름의 폴더가 이미 존재함"
                body<ApiResponse<ErrorDetail>> {
                    examples(ErrorCode.FOLDER_DUPLICATE_NAME)
                }
            }
        }
    }

internal fun updateFolderDocs(): RouteConfig.() -> Unit =
    {
        summary = "폴더 수정 API"
        request {
            pathParameter<Long>("id") { description = "폴더 ID" }
            body<UpdateFolderRequest>()
        }
        response {
            code(HttpStatusCode.OK) {
                description = "폴더 수정 성공"
                body<ApiResponse<UpdateFolderResponse>>()
            }
            code(HttpStatusCode.BadRequest) {
                description = "폴더 수정 요청 유효성 검증 실패"
                body<ApiResponse<ErrorDetail>> {
                    examples(
                        ErrorCode.FOLDER_NAME_BLANK,
                        ErrorCode.FOLDER_NAME_OVER_MAX,
                        ErrorCode.FOLDER_EMOJI_OVER_MAX,
                        ErrorCode.FOLDER_EMOJI_INVALID,
                    )
                }
            }
            authErrorResponse()
            code(HttpStatusCode.Forbidden) {
                description = "폴더 수정 권한 검증 실패"
                body<ApiResponse<ErrorDetail>> {
                    examples(ErrorCode.FOLDER_DIFFERENT_OWNER)
                }
            }
            code(HttpStatusCode.NotFound) {
                description = "폴더 수정 대상 리소스 조회 실패"
                body<ApiResponse<ErrorDetail>> {
                    examples(
                        ErrorCode.FOLDER_OWNER_NOT_FOUND,
                        ErrorCode.FOLDER_NOT_FOUND,
                    )
                }
            }
            code(HttpStatusCode.Conflict) {
                description = "같은 이름의 폴더가 이미 존재함"
                body<ApiResponse<ErrorDetail>> {
                    examples(ErrorCode.FOLDER_DUPLICATE_NAME)
                }
            }
        }
    }

internal fun deleteFolderDocs(): RouteConfig.() -> Unit =
    {
        summary = "폴더 삭제 API"
        description =
            """
            onDelete는 대소문자를 구분하지 않는 null / cascade / move이며 생략하면 null로 처리돼요.
            null은 모든 링크를 각 소유자의 미분류로 보내요. 개인 폴더의 move는 내 링크만 대상 폴더로,
            타인 링크는 미분류로 보내요. 개인 폴더의 cascade는 내 링크만 삭제하고 타인 링크는 미분류로 보내요.
            공유 폴더는 move를 지원하지 않으며 cascade는 모든 링크와 액션·AI 작업·미발송 예약 알림을 삭제해요.
            존재하지 않는 원본 폴더는 옵션과 대상의 업무 검사 없이 성공해요.
            """.trimIndent()
        request {
            pathParameter<Long>("id") { description = "폴더 ID" }
            queryParameter<String?>("onDelete") { description = "링크 처리 방법 (null / cascade / move), 기본값: null" }
            queryParameter<Long?>("targetFolderId") { description = "move일 때 필수인 대상 폴더 ID예요. 다른 옵션에는 제공할 수 없어요. 0도 실제 폴더 ID로 조회돼요." }
        }
        response {
            code(HttpStatusCode.OK) {
                description = "폴더 삭제 성공"
                body<EmptySuccessResponse>()
            }
            code(HttpStatusCode.BadRequest) {
                description = "폴더 삭제 요청이 올바르지 않음"
                body<ApiResponse<ErrorDetail>> {
                    examples(
                        ErrorCode.COMMON_BAD_REQUEST,
                        ErrorCode.FOLDER_DELETE_TARGET_REQUIRED,
                        ErrorCode.FOLDER_DELETE_TARGET_SELF,
                    )
                }
            }
            authErrorResponse()
            code(HttpStatusCode.Forbidden) {
                description = "폴더 삭제 권한 검증 실패"
                body<ApiResponse<ErrorDetail>> {
                    examples(
                        ErrorCode.FOLDER_DIFFERENT_OWNER,
                        ErrorCode.FOLDER_DELETE_TARGET_ACCESS_DENIED,
                    )
                }
            }
            code(HttpStatusCode.NotFound) {
                description = "폴더 삭제에 필요한 리소스 조회 실패"
                body<ApiResponse<ErrorDetail>> {
                    examples(
                        ErrorCode.FOLDER_OWNER_NOT_FOUND,
                        ErrorCode.FOLDER_DELETE_TARGET_NOT_FOUND,
                    )
                }
            }
            code(HttpStatusCode.UnprocessableEntity) {
                description = "공유 폴더는 move를 지원하지 않음"
                body<ApiResponse<ErrorDetail>> {
                    examples(ErrorCode.FOLDER_DELETE_MOVE_SHARED)
                }
            }
        }
    }

internal fun deleteFolderMemberDocs(): RouteConfig.() -> Unit =
    {
        summary = "공유 멤버 삭제 API"
        request {
            pathParameter<Long>("id") { description = "폴더 ID" }
            pathParameter<Long>("memberId") { description = "삭제할 멤버 사용자 ID" }
        }
        response {
            code(HttpStatusCode.OK) {
                description = "공유 멤버 삭제 성공"
                body<EmptySuccessResponse>()
            }
            authErrorResponse()
            code(HttpStatusCode.Forbidden) {
                description = "공유 멤버 삭제 권한 검증 실패"
                body<ApiResponse<ErrorDetail>> {
                    examples(ErrorCode.FOLDER_DIFFERENT_OWNER)
                }
            }
            code(HttpStatusCode.NotFound) {
                description = "공유 멤버 삭제에 필요한 리소스 조회 실패"
                body<ApiResponse<ErrorDetail>> {
                    examples(ErrorCode.FOLDER_OWNER_NOT_FOUND, ErrorCode.FOLDER_NOT_FOUND)
                }
            }
            code(HttpStatusCode.UnprocessableEntity) {
                description = "소유자는 소유권 위임 전에 나갈 수 없음"
                body<ApiResponse<ErrorDetail>> {
                    examples(ErrorCode.FOLDER_OWNER_CANNOT_LEAVE)
                }
            }
        }
    }

internal fun getFolderMembersDocs(): RouteConfig.() -> Unit =
    {
        summary = "공유 폴더 멤버 조회 API"
        request {
            pathParameter<Long>("id") { description = "폴더 ID" }
        }
        response {
            code(HttpStatusCode.OK) {
                description = "공유 폴더 멤버 조회 성공"
                body<ApiResponse<GetFolderMembersResponse>>()
            }
            authErrorResponse()
            code(HttpStatusCode.Forbidden) {
                description = "공유 폴더 멤버 조회 권한 검증 실패"
                body<ApiResponse<ErrorDetail>> {
                    examples(ErrorCode.FOLDER_MEMBER_ACCESS_DENIED)
                }
            }
            code(HttpStatusCode.NotFound) {
                description = "공유 폴더 멤버 조회에 필요한 리소스 조회 실패"
                body<ApiResponse<ErrorDetail>> {
                    examples(
                        ErrorCode.FOLDER_MEMBER_OWNER_NOT_FOUND,
                        ErrorCode.FOLDER_MEMBER_FOLDER_NOT_FOUND,
                    )
                }
            }
            code(HttpStatusCode.UnprocessableEntity) {
                description = "공유 폴더가 아님"
                body<ApiResponse<ErrorDetail>> {
                    examples(ErrorCode.FOLDER_MEMBER_NOT_SHARED_FOLDER)
                }
            }
        }
    }

internal fun createFolderInvitationDocs(): RouteConfig.() -> Unit =
    {
        summary = "공유 폴더 초대 토큰 생성 API"
        request {
            pathParameter<Long>("id") { description = "폴더 ID" }
            body<CreateFolderInvitationRequest>()
        }
        response {
            code(HttpStatusCode.Created) {
                description = "공유 폴더 초대 생성 성공"
                body<ApiResponse<CreateFolderInvitationResponse>>()
            }
            authErrorResponse()
            code(HttpStatusCode.Forbidden) {
                description = "공유 폴더 초대 생성 권한 검증 실패"
                body<ApiResponse<ErrorDetail>> {
                    examples(ErrorCode.FOLDER_DIFFERENT_OWNER)
                }
            }
            code(HttpStatusCode.NotFound) {
                description = "공유 폴더 초대 생성 대상 리소스 조회 실패"
                body<ApiResponse<ErrorDetail>> {
                    examples(
                        ErrorCode.FOLDER_OWNER_NOT_FOUND,
                        ErrorCode.FOLDER_NOT_FOUND,
                    )
                }
            }
            code(HttpStatusCode.UnprocessableEntity) {
                description = "공유 폴더가 아님"
                body<ApiResponse<ErrorDetail>> {
                    examples(ErrorCode.FOLDER_NOT_SHARED)
                }
            }
        }
    }

internal fun acceptFolderInvitationDocs(): RouteConfig.() -> Unit =
    {
        summary = "공유 폴더 초대 수락 API"
        request {
            pathParameter<Long>("id") { description = "폴더 ID" }
            body<AcceptFolderInvitationRequest>()
        }
        response {
            code(HttpStatusCode.OK) {
                description = "공유 폴더 초대 수락 성공"
                body<ApiResponse<AcceptFolderInvitationResponse>>()
            }
            code(HttpStatusCode.BadRequest) {
                description = "공유 폴더 초대 수락 요청이 올바르지 않음"
                body<ApiResponse<ErrorDetail>> {
                    examples(
                        ErrorCode.COMMON_BAD_REQUEST,
                        ErrorCode.FOLDER_INVITATION_INVALID,
                    )
                }
            }
            authErrorResponse()
            code(HttpStatusCode.NotFound) {
                description = "공유 폴더 초대 수락 대상 리소스 조회 실패"
                body<ApiResponse<ErrorDetail>> {
                    examples(ErrorCode.FOLDER_NOT_FOUND)
                }
            }
            code(HttpStatusCode.UnprocessableEntity) {
                description = "공유 폴더 초대가 만료됨"
                body<ApiResponse<ErrorDetail>> {
                    examples(ErrorCode.FOLDER_INVITATION_EXPIRED)
                }
            }
        }
    }

internal fun transferFolderOwnerDocs(): RouteConfig.() -> Unit =
    {
        summary = "공유 폴더 소유권 위임 API"
        description = "현재 멤버에게 소유권을 위임하고 이전 소유자는 나가게 돼요. 링크·액션·초대는 보존돼요."
        request {
            pathParameter<Long>("id") { description = "폴더 ID" }
            body<TransferFolderOwnerRequest>()
        }
        response {
            code(HttpStatusCode.OK) {
                description = "소유권 위임 성공"
                body<EmptySuccessResponse>()
            }
            code(HttpStatusCode.BadRequest) {
                description = "요청이 올바르지 않거나 자신에게 위임함"
                body<ApiResponse<ErrorDetail>> {
                    examples(ErrorCode.COMMON_BAD_REQUEST, ErrorCode.FOLDER_TRANSFER_TO_SELF)
                }
            }
            authErrorResponse()
            code(HttpStatusCode.Forbidden) {
                description = "현재 소유자가 아님"
                body<ApiResponse<ErrorDetail>> {
                    examples(ErrorCode.FOLDER_DIFFERENT_OWNER)
                }
            }
            code(HttpStatusCode.NotFound) {
                description = "위임에 필요한 리소스 조회 실패"
                body<ApiResponse<ErrorDetail>> {
                    examples(
                        ErrorCode.FOLDER_TRANSFER_CALLER_NOT_FOUND,
                        ErrorCode.FOLDER_TRANSFER_FOLDER_NOT_FOUND,
                        ErrorCode.FOLDER_TRANSFER_MEMBER_NOT_FOUND,
                    )
                }
            }
            code(HttpStatusCode.Conflict) {
                description = "위임 대상이 같은 이름의 다른 폴더를 소유함"
                body<ApiResponse<ErrorDetail>> {
                    examples(ErrorCode.FOLDER_TRANSFER_DUPLICATE_NAME)
                }
            }
            code(HttpStatusCode.UnprocessableEntity) {
                description = "공유 폴더가 아님"
                body<ApiResponse<ErrorDetail>> {
                    examples(ErrorCode.FOLDER_TRANSFER_NOT_SHARED)
                }
            }
        }
    }
