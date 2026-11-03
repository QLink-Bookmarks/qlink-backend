package com.qlink.bookmarkimport.domain

import com.qlink.common.error.ErrorCode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContainOnly

class ImportFailureReasonTest :
    BehaviorSpec({
        Given("링크 검증 실패 ErrorCode") {
            val urlErrors =
                listOf(
                    ErrorCode.LINK_URL_BLANK,
                    ErrorCode.LINK_URL_WRONG_FORMAT,
                    ErrorCode.LINK_URL_WRONG_HOST,
                    ErrorCode.LINK_URL_NOT_HTTP,
                )
            val titleErrors = listOf(ErrorCode.LINK_TITLE_BLANK, ErrorCode.LINK_TITLE_OVER_MAX)

            When("LINK_URL_BLANK·LINK_URL_WRONG_FORMAT·LINK_URL_WRONG_HOST·LINK_URL_NOT_HTTP면") {
                val reasons = urlErrors.map(ImportFailureReason::from)

                Then("URL_INVALID") {
                    reasons shouldContainOnly listOf(ImportFailureReason.URL_INVALID)
                }
            }

            When("LINK_TITLE_BLANK·LINK_TITLE_OVER_MAX면") {
                val reasons = titleErrors.map(ImportFailureReason::from)

                Then("TITLE_INVALID") {
                    reasons shouldContainOnly listOf(ImportFailureReason.TITLE_INVALID)
                }
            }

            When("그 밖의 ErrorCode면") {
                val reasons = ErrorCode.entries.filterNot { it in urlErrors || it in titleErrors }.map(ImportFailureReason::from)

                Then("UNKNOWN") {
                    reasons shouldContainOnly listOf(ImportFailureReason.UNKNOWN)
                }
            }
        }
    })
