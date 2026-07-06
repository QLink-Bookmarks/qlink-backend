package com.qlink.post.domain

import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrowWithMessage
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class PostTypeTest :
    BehaviorSpec({
        Given("PostType 도메인") {
            When("from 으로 문자열을 파싱하면") {
                Then("대소문자 무관하게 매핑된다") {
                    PostType.from("announcement") shouldBe PostType.ANNOUNCEMENT
                    PostType.from("ANNOUNCEMENT") shouldBe PostType.ANNOUNCEMENT
                    PostType.from("Feedback") shouldBe PostType.FEEDBACK
                }

                Then("지원하지 않는 값이면 예외를 던진다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.POST_TYPE_NOT_SUPPORTED.message) {
                        PostType.from("unknown")
                    }
                }
            }

            When("validateManageable 로 권한을 검증하면") {
                Then("ANNOUNCEMENT 는 관리자만 허용된다") {
                    shouldNotThrowAny { PostType.ANNOUNCEMENT.validateManageable(isAdmin = true) }
                    shouldThrowWithMessage<BusinessException>(ErrorCode.POST_ANNOUNCEMENT_FORBIDDEN.message) {
                        PostType.ANNOUNCEMENT.validateManageable(isAdmin = false)
                    }
                }

                Then("FEEDBACK 은 관리자가 아니어도 가능하다") {
                    shouldNotThrowAny { PostType.FEEDBACK.validateManageable(isAdmin = false) }
                }
            }

            When("validateReadable 로 조회 권한을 검증하면") {
                Then("FEEDBACK 은 관리자만 조회 가능하다") {
                    shouldNotThrowAny { PostType.FEEDBACK.validateReadable(isAdmin = true) }
                    shouldThrowWithMessage<BusinessException>(ErrorCode.POST_FEEDBACK_FORBIDDEN.message) {
                        PostType.FEEDBACK.validateReadable(isAdmin = false)
                    }
                }

                Then("ANNOUNCEMENT 는 관리자가 아니어도 조회 가능하다") {
                    shouldNotThrowAny { PostType.ANNOUNCEMENT.validateReadable(isAdmin = false) }
                }
            }

            When("hasImage 를 확인하면") {
                Then("FEEDBACK 만 이미지를 가진다") {
                    PostType.FEEDBACK.hasImage shouldBe true
                    PostType.ANNOUNCEMENT.hasImage shouldBe false
                }
            }
        }
    })
