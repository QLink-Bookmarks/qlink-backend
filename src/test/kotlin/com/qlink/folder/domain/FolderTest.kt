package com.qlink.folder.domain

import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import com.qlink.support.fixture.FolderFixture
import com.qlink.support.fixture.RandomFixture
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrowWithMessage
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldNotBe

class FolderTest :
    BehaviorSpec({
        Given("폴더 생성 테스트") {
            val ownerId = RandomFixture.randomId()

            When("유효한 값으로 생성하면") {
                val create = {
                    Folder.create(
                        ownerId = ownerId,
                        name = RandomFixture.randomSentenceWithMax(100),
                        emoji = RandomFixture.randomEmoji(),
                        sharedAt = null,
                    )
                }

                Then("성공한다") {
                    shouldNotThrowAny {
                        create()
                    }
                }
            }

            When("이모지가 없으면") {
                val folder =
                    Folder.create(
                        ownerId = ownerId,
                        name = RandomFixture.randomSentenceWithMax(100),
                        emoji = null,
                        sharedAt = null,
                    )

                Then("기본 이모지가 채워진다") {
                    folder.emoji shouldNotBe null
                }
            }

            When("이름이 비어 있으면") {
                val create = {
                    Folder.create(
                        ownerId = ownerId,
                        name = " ",
                        emoji = RandomFixture.randomEmoji(),
                        sharedAt = null,
                    )
                }

                Then("예외가 발생한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.FOLDER_NAME_BLANK.message) {
                        create()
                    }
                }
            }

            When("이름이 최대 길이를 초과하면") {
                val create = {
                    Folder.create(
                        ownerId = ownerId,
                        name = RandomFixture.randomFixedSentence(101),
                        emoji = RandomFixture.randomEmoji(),
                        sharedAt = null,
                    )
                }

                Then("예외가 발생한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.FOLDER_NAME_OVER_MAX.message) {
                        create()
                    }
                }
            }

            When("이모지 길이가 최대 길이를 초과하면") {
                val create = {
                    Folder.create(
                        ownerId = ownerId,
                        name = RandomFixture.randomSentenceWithMax(100),
                        emoji = RandomFixture.randomFixedSentence(21),
                        sharedAt = null,
                    )
                }

                Then("예외가 발생한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.FOLDER_EMOJI_OVER_MAX.message) {
                        create()
                    }
                }
            }

            When("이모지 형식이 잘못되면") {
                val create = {
                    Folder.create(
                        ownerId = ownerId,
                        name = RandomFixture.randomSentenceWithMax(100),
                        emoji = "folder",
                        sharedAt = null,
                    )
                }

                Then("예외가 발생한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.FOLDER_EMOJI_INVALID.message) {
                        create()
                    }
                }
            }
        }

        Given("소유자 검증 테스트") {
            val ownerId = RandomFixture.randomId()
            val folder = FolderFixture.createValidUnsharedFolder(ownerId)

            When("소유자가 맞으면") {
                val validate = {
                    folder.validateOwner(ownerId)
                }

                Then("검증을 성공한다") {
                    shouldNotThrowAny {
                        validate()
                    }
                }
            }

            When("소유자가 다르면") {
                val differentUserId = RandomFixture.randomId()
                val validate = {
                    folder.validateOwner(differentUserId)
                }

                Then("검증을 실패한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.FOLDER_DIFFERENT_OWNER.message) {
                        validate()
                    }
                }
            }
        }

        Given("폴더 수정 테스트") {
            val folder = FolderFixture.createValidUnsharedFolder(RandomFixture.randomId())

            When("유효한 값으로 수정하면") {
                val update = {
                    folder.update(
                        name = RandomFixture.randomSentenceWithMax(100),
                        emoji = RandomFixture.randomEmoji(),
                    )
                }

                Then("성공한다") {
                    shouldNotThrowAny {
                        update()
                    }
                }
            }

            When("이름이 비어 있으면") {
                val update = {
                    folder.update(
                        name = " ",
                        emoji = RandomFixture.randomEmoji(),
                    )
                }

                Then("예외가 발생한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.FOLDER_NAME_BLANK.message) {
                        update()
                    }
                }
            }

            When("이름이 최대 길이를 초과하면") {
                val update = {
                    folder.update(
                        name = RandomFixture.randomFixedSentence(101),
                        emoji = RandomFixture.randomEmoji(),
                    )
                }

                Then("예외가 발생한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.FOLDER_NAME_OVER_MAX.message) {
                        update()
                    }
                }
            }

            When("이모지 길이가 최대 길이를 초과하면") {
                val update = {
                    folder.update(
                        name = RandomFixture.randomSentenceWithMax(100),
                        emoji = RandomFixture.randomFixedSentence(21),
                    )
                }

                Then("예외가 발생한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.FOLDER_EMOJI_OVER_MAX.message) {
                        update()
                    }
                }
            }

            When("이모지 형식이 잘못되면") {
                val update = {
                    folder.update(
                        name = RandomFixture.randomSentenceWithMax(100),
                        emoji = "folder",
                    )
                }

                Then("예외가 발생한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.FOLDER_EMOJI_INVALID.message) {
                        update()
                    }
                }
            }
        }
    })
