package com.qlink.todo.domain

import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import com.qlink.common.search.SearchOrder
import com.qlink.common.search.SortDirection
import io.kotest.assertions.throwables.shouldNotThrow
import io.kotest.assertions.throwables.shouldThrowWithMessage
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class TodoSortTest :
    BehaviorSpec({
        Given("액션 정렬 요청을 정할 때") {
            When("기준과 방향이 모두 없으면") {
                val actual = TodoSort.of(orderKey = null, order = null)

                Then("만든 날 내림차순이다") {
                    actual.orderKey shouldBe TodoOrderKey.CREATED_AT
                    actual.direction shouldBe SortDirection.DESC
                }
            }

            When("기준만 알림일이면") {
                val actual = TodoSort.of(orderKey = "reminder_at", order = null)

                Then("알림일 오름차순이다") {
                    actual.orderKey shouldBe TodoOrderKey.REMINDER_AT
                    actual.direction shouldBe SortDirection.ASC
                }
            }

            When("기준만 만든 날이면") {
                val actual = TodoSort.of(orderKey = "created_at", order = null)

                Then("만든 날 내림차순이다") {
                    actual.orderKey shouldBe TodoOrderKey.CREATED_AT
                    actual.direction shouldBe SortDirection.DESC
                }
            }

            When("기준만 완료일이면") {
                val actual = TodoSort.of(orderKey = "completed_at", order = null)

                Then("완료일 내림차순이다") {
                    actual.orderKey shouldBe TodoOrderKey.COMPLETED_AT
                    actual.direction shouldBe SortDirection.DESC
                }
            }

            When("방향만 오름차순이면") {
                val actual = TodoSort.of(orderKey = null, order = "asc")

                Then("만든 날 오름차순이다") {
                    actual.orderKey shouldBe TodoOrderKey.CREATED_AT
                    actual.direction shouldBe SortDirection.ASC
                }
            }

            When("각 기준에 반대 방향을 보내면") {
                val actual =
                    listOf("reminder_at" to "desc", "created_at" to "asc", "completed_at" to "asc")
                        .map { (orderKey, order) -> TodoSort.of(orderKey = orderKey, order = order) }

                Then("그 기준·방향이다") {
                    actual.map { it.orderKey to it.direction } shouldBe
                        listOf(
                            TodoOrderKey.REMINDER_AT to SortDirection.DESC,
                            TodoOrderKey.CREATED_AT to SortDirection.ASC,
                            TodoOrderKey.COMPLETED_AT to SortDirection.ASC,
                        )
                }
            }

            When("기존 값 latest이면") {
                val actual = TodoSort.of(orderKey = null, order = "latest")

                Then("만든 날 내림차순이다") {
                    actual.orderKey shouldBe TodoOrderKey.CREATED_AT
                    actual.direction shouldBe SortDirection.DESC
                }
            }

            When("기존 값 earliest·laxico·similar이면") {
                val ofs = listOf("earliest", "laxico", "similar").map { { TodoSort.of(orderKey = null, order = it) } }

                Then("예외를 반환한다") {
                    ofs.forEach { of ->
                        shouldThrowWithMessage<BusinessException>(ErrorCode.COMMON_INVALID_SORT_ORDER.message) {
                            of()
                        }
                    }
                }
            }

            When("기준과 기존 값을 함께 보내면") {
                val of = { TodoSort.of(orderKey = "created_at", order = "latest") }

                Then("예외를 반환한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.COMMON_INVALID_SORT_ORDER.message) {
                        of()
                    }
                }
            }

            When("링크 기준처럼 지원하지 않는 기준이면") {
                val ofs = listOf("title", "similarity").map { { TodoSort.of(orderKey = it, order = null) } }

                Then("예외를 반환한다") {
                    ofs.forEach { of ->
                        shouldThrowWithMessage<BusinessException>(ErrorCode.COMMON_INVALID_SORT_ORDER.message) {
                            of()
                        }
                    }
                }
            }

            When("지원하지 않는 방향 값이면") {
                val of = { TodoSort.of(orderKey = "reminder_at", order = "unknown") }

                Then("예외를 반환한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.COMMON_INVALID_SORT_ORDER.message) {
                        of()
                    }
                }
            }
        }

        Given("만든 날 내림차순 정렬") {
            val sort = TodoSort.of(orderKey = null, order = null)

            When("같은 기준·방향의 커서이면") {
                val validate = { sort.validateCursor(orderKey = TodoOrderKey.CREATED_AT, direction = SortDirection.DESC, order = null) }

                Then("예외 없이 통과한다") {
                    shouldNotThrow<BusinessException> { validate() }
                }
            }

            When("기존 값 LATEST만 든 커서이면") {
                val validate = { sort.validateCursor(orderKey = null, direction = null, order = SearchOrder.LATEST) }

                Then("예외 없이 통과한다") {
                    shouldNotThrow<BusinessException> { validate() }
                }
            }

            When("기존 값 EARLIEST만 든 커서이면") {
                val validate = { sort.validateCursor(orderKey = null, direction = null, order = SearchOrder.EARLIEST) }

                Then("예외를 반환한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.COMMON_CURSOR_ORDER_MISMATCH.message) {
                        validate()
                    }
                }
            }

            When("기준이 다른 커서이면") {
                val validate = { sort.validateCursor(orderKey = TodoOrderKey.REMINDER_AT, direction = SortDirection.DESC, order = null) }

                Then("예외를 반환한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.COMMON_CURSOR_ORDER_MISMATCH.message) {
                        validate()
                    }
                }
            }

            When("방향이 다른 커서이면") {
                val validate = { sort.validateCursor(orderKey = TodoOrderKey.CREATED_AT, direction = SortDirection.ASC, order = null) }

                Then("예외를 반환한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.COMMON_CURSOR_ORDER_MISMATCH.message) {
                        validate()
                    }
                }
            }
        }
    })
