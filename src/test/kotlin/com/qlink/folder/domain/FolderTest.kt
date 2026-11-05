package com.qlink.folder.domain

import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import com.qlink.support.fixture.FolderFixture
import com.qlink.support.fixture.RandomFixture
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrowWithMessage
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
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

        Given("가져온 폴더를 만들 때") {
            val ownerId = RandomFixture.randomId()
            val name = RandomFixture.randomSentenceWithMax(100)
            val createdAt = RandomFixture.randomPastInstant()

            When("유효하면") {
                val folder = Folder.createImported(ownerId = ownerId, name = name, createdAt = createdAt)

                Then("주어진 소유자·이름, createdAt·updatedAt이 주어진 시각, 기본 이모지 중 하나") {
                    folder.ownerId shouldBe ownerId
                    folder.name shouldBe name
                    folder.createdAt shouldBe createdAt
                    folder.updatedAt shouldBe createdAt
                    folder.sharedAt shouldBe null
                    folder.emoji shouldNotBe null
                }
            }
        }

        Given("공유 여부가 다른 폴더") {
            val ownerId = RandomFixture.randomId()

            When("sharedAt이 있으면") {
                val folder = FolderFixture.createSharedFolderOf(ownerId = ownerId)

                Then("true") {
                    folder.isShared shouldBe true
                }
            }

            When("sharedAt이 없으면") {
                val folder = FolderFixture.createValidUnsharedFolder(ownerId)

                Then("false") {
                    folder.isShared shouldBe false
                }
            }
        }
        Given("공유 폴더 소유권과 멤버 탈퇴 검증") {
            val ownerId = RandomFixture.randomId()
            val memberId = RandomFixture.randomId()
            val otherId = RandomFixture.randomId()
            val folder = FolderFixture.createSharedFolderOf(ownerId = ownerId)

            When("현재 소유자를 검증하면") {
                val validate = { folder.validateOwner(ownerId) }
                Then("소유 상태를 유지한다") {
                    shouldNotThrowAny { validate() }
                    folder.ownerId shouldBe ownerId
                }
            }
            When("다른 사용자를 소유자로 검증하면") {
                val validate = { folder.validateOwner(memberId) }
                Then("소유자 오류다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.FOLDER_DIFFERENT_OWNER.message) { validate() }
                }
            }
            When("다른 멤버에게 위임할 수 있는지 검증하면") {
                val validate = { folder.validateTransferTo(memberId) }
                Then("공유와 대상 검증을 통과한다") { shouldNotThrowAny { validate() } }
            }
            When("자기 자신에게 위임하면") {
                val validate = { folder.validateTransferTo(ownerId) }
                Then("자기 위임 오류다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.FOLDER_TRANSFER_TO_SELF.message) { validate() }
                }
            }
            When("멤버 자신이 나가면") {
                val validate = { folder.validateLeavable(memberId) }
                Then("통과하고 소유자를 유지한다") {
                    shouldNotThrowAny { validate() }
                    folder.ownerId shouldBe ownerId
                }
            }
            When("소유자가 나가면") {
                val validate = { folder.validateLeavable(ownerId) }
                Then("소유자 탈퇴 오류다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.FOLDER_OWNER_CANNOT_LEAVE.message) { validate() }
                }
            }
            When("비소유자가 자기 member를 삭제하면") {
                val validate = { folder.validateMemberRemoval(loginId = memberId, memberId = memberId) }
                Then("허용한다") { shouldNotThrowAny { validate() } }
            }
            When("소유자가 다른 member를 삭제하면") {
                val validate = { folder.validateMemberRemoval(loginId = ownerId, memberId = memberId) }
                Then("허용한다") { shouldNotThrowAny { validate() } }
            }
            When("비소유자가 타인을 삭제하면") {
                val validate = { folder.validateMemberRemoval(loginId = memberId, memberId = otherId) }
                Then("소유자 검증으로 거절된다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.FOLDER_DIFFERENT_OWNER.message) { validate() }
                }
            }
            When("공유 source에서 move를 검증하면") {
                val validate = { folder.validateMoveTarget(RandomFixture.randomId()) }
                Then("공유 이동 오류다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.FOLDER_DELETE_MOVE_SHARED.message) { validate() }
                }
            }
        }

        Given("공유 해제된 개인 폴더") {
            val ownerId = RandomFixture.randomId()
            val folder = FolderFixture.createValidUnsharedFolder(ownerId = ownerId)
            When("다른 사용자에게 위임하면") {
                val validate = { folder.validateTransferTo(RandomFixture.randomId()) }
                Then("비공유 위임 오류다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.FOLDER_TRANSFER_NOT_SHARED.message) { validate() }
                }
            }
            When("소유자가 나가면") {
                val validate = { folder.validateLeavable(ownerId) }
                Then("공유 상태와 무관하게 거절한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.FOLDER_OWNER_CANNOT_LEAVE.message) { validate() }
                }
            }
            When("다른 폴더로 move를 검증하면") {
                val validate = { folder.validateMoveTarget(RandomFixture.randomId()) }
                Then("허용한다") { shouldNotThrowAny { validate() } }
            }
            When("source 자신으로 move를 검증하면") {
                val validate = { folder.validateMoveTarget(folder.id!!) }
                Then("자기 이동 오류다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.FOLDER_DELETE_TARGET_SELF.message) { validate() }
                }
            }
        }
        Given("폴더의 링크 쓰기 권한") {
            val callerId = RandomFixture.randomId()
            val owned = FolderFixture.createValidUnsharedFolder(ownerId = callerId)
            val shared = FolderFixture.createSharedFolderOf(ownerId = RandomFixture.randomId())
            val private = FolderFixture.createValidUnsharedFolder(ownerId = RandomFixture.randomId())

            When("소유자는 member 권한이 없어도") {
                Then("쓸 수 있다") {
                    owned.isLinkWritableBy(userId = callerId, memberCanWrite = false) shouldBe true
                }
            }
            When("비소유자가 공유 폴더의 member 쓰기 권한을 가지면") {
                Then("쓸 수 있다") {
                    shared.isLinkWritableBy(userId = callerId, memberCanWrite = true) shouldBe true
                }
            }
            When("비소유자가 공유 폴더의 member 쓰기 권한이 없으면") {
                Then("쓸 수 없다") {
                    shared.isLinkWritableBy(userId = callerId, memberCanWrite = false) shouldBe false
                }
            }
            When("개인 폴더에 stale member 쓰기 권한만 있으면") {
                Then("쓸 수 없다") {
                    private.isLinkWritableBy(userId = callerId, memberCanWrite = true) shouldBe false
                }
            }
        }
    })
