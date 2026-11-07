package com.qlink.link.domain

import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import com.qlink.common.search.SearchOrder
import com.qlink.common.search.SortDirection
import com.qlink.support.fixture.RandomFixture
import io.kotest.assertions.throwables.shouldNotThrow
import io.kotest.assertions.throwables.shouldThrowWithMessage
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class LinkSortTest :
    BehaviorSpec({
        Given("링크 정렬 요청을 정할 때") {
            When("기준과 방향이 모두 없으면") {
                val actual = LinkSort.of(orderKey = null, order = null)

                Then("저장일 내림차순이다") {
                    actual.orderKey shouldBe LinkOrderKey.CREATED_AT
                    actual.direction shouldBe SortDirection.DESC
                }
            }

            When("기준만 저장일이면") {
                val actual = LinkSort.of(orderKey = "created_at", order = null)

                Then("저장일 내림차순이다") {
                    actual.orderKey shouldBe LinkOrderKey.CREATED_AT
                    actual.direction shouldBe SortDirection.DESC
                }
            }

            When("기준만 제목이면") {
                val actual = LinkSort.of(orderKey = "title", order = null)

                Then("제목 오름차순이다") {
                    actual.orderKey shouldBe LinkOrderKey.TITLE
                    actual.direction shouldBe SortDirection.ASC
                }
            }

            When("기준만 유사도이면") {
                val actual = LinkSort.of(orderKey = "similarity", order = null)

                Then("유사도 내림차순이다") {
                    actual.orderKey shouldBe LinkOrderKey.SIMILARITY
                    actual.direction shouldBe SortDirection.DESC
                }
            }

            When("방향만 오름차순이면") {
                val actual = LinkSort.of(orderKey = null, order = "asc")

                Then("저장일 오름차순이다") {
                    actual.orderKey shouldBe LinkOrderKey.CREATED_AT
                    actual.direction shouldBe SortDirection.ASC
                }
            }

            When("제목 내림차순이면") {
                val actual = LinkSort.of(orderKey = "title", order = "desc")

                Then("제목 내림차순이다") {
                    actual.orderKey shouldBe LinkOrderKey.TITLE
                    actual.direction shouldBe SortDirection.DESC
                }
            }

            When("기준·방향을 대소문자를 섞어 보내면") {
                val actual = LinkSort.of(orderKey = RandomFixture.randomCase("title"), order = RandomFixture.randomCase("desc"))

                Then("소문자로 보낸 것과 같은 정렬이다") {
                    actual.orderKey shouldBe LinkOrderKey.TITLE
                    actual.direction shouldBe SortDirection.DESC
                }
            }

            When("기준·방향이 빈 문자열이면") {
                val actual = LinkSort.of(orderKey = "", order = " ")

                Then("값이 없을 때와 같은 기본 정렬이다") {
                    actual.orderKey shouldBe LinkOrderKey.CREATED_AT
                    actual.direction shouldBe SortDirection.DESC
                }
            }

            When("기존 값 latest·earliest·laxico·similar를 각각 보내면") {
                val actual = listOf("latest", "earliest", "laxico", "similar").map { LinkSort.of(orderKey = null, order = it) }

                Then("저장일 내림차순·저장일 오름차순·제목 오름차순·유사도 내림차순이다") {
                    actual.map { it.orderKey to it.direction } shouldBe
                        listOf(
                            LinkOrderKey.CREATED_AT to SortDirection.DESC,
                            LinkOrderKey.CREATED_AT to SortDirection.ASC,
                            LinkOrderKey.TITLE to SortDirection.ASC,
                            LinkOrderKey.SIMILARITY to SortDirection.DESC,
                        )
                }
            }

            When("기준과 기존 값을 함께 보내면") {
                val of = { LinkSort.of(orderKey = "created_at", order = "latest") }

                Then("예외를 반환한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.COMMON_INVALID_SORT_ORDER.message) {
                        of()
                    }
                }
            }

            When("지원하지 않는 기준이면") {
                val of = { LinkSort.of(orderKey = "reminder_at", order = null) }

                Then("예외를 반환한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.COMMON_INVALID_SORT_ORDER.message) {
                        of()
                    }
                }
            }

            When("지원하지 않는 방향 값이면") {
                val of = { LinkSort.of(orderKey = null, order = "unknown") }

                Then("예외를 반환한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.COMMON_INVALID_SORT_ORDER.message) {
                        of()
                    }
                }
            }

            When("유사도 오름차순이면") {
                val of = { LinkSort.of(orderKey = "similarity", order = "asc") }

                Then("예외를 반환한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.COMMON_INVALID_SORT_ORDER.message) {
                        of()
                    }
                }
            }
        }

        Given("저장일 내림차순 정렬") {
            val sort = LinkSort.of(orderKey = "created_at", order = "desc")

            When("같은 기준·방향의 커서이면") {
                val validate = { sort.validateCursor(orderKey = LinkOrderKey.CREATED_AT, direction = SortDirection.DESC, order = null) }

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

            When("방향이 다른 커서이면") {
                val validate = { sort.validateCursor(orderKey = LinkOrderKey.CREATED_AT, direction = SortDirection.ASC, order = null) }

                Then("예외를 반환한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.COMMON_CURSOR_ORDER_MISMATCH.message) {
                        validate()
                    }
                }
            }

            When("기준이 다른 커서이면") {
                val validate = { sort.validateCursor(orderKey = LinkOrderKey.TITLE, direction = SortDirection.DESC, order = null) }

                Then("예외를 반환한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.COMMON_CURSOR_ORDER_MISMATCH.message) {
                        validate()
                    }
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

            When("기준·방향·기존 값이 모두 없는 커서이면") {
                val validate = { sort.validateCursor(orderKey = null, direction = null, order = null) }

                Then("예외를 반환한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.COMMON_CURSOR_ORDER_MISMATCH.message) {
                        validate()
                    }
                }
            }
        }

        Given("제목 오름차순 정렬") {
            val sort = LinkSort.of(orderKey = "title", order = "asc")

            When("기존 값 LAXICO만 든 커서이면") {
                val validate = { sort.validateCursor(orderKey = null, direction = null, order = SearchOrder.LAXICO) }

                Then("예외 없이 통과한다") {
                    shouldNotThrow<BusinessException> { validate() }
                }
            }
        }

        Given("유사도 내림차순 정렬") {
            val sort = LinkSort.of(orderKey = "similarity", order = null)

            When("기존 값 SIMILAR만 든 커서이면") {
                val validate = { sort.validateCursor(orderKey = null, direction = null, order = SearchOrder.SIMILAR) }

                Then("예외 없이 통과한다") {
                    shouldNotThrow<BusinessException> { validate() }
                }
            }
        }
    })
