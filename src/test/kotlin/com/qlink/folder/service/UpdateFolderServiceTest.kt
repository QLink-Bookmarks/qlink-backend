package com.qlink.folder.service

import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import com.qlink.folder.domain.Folder
import com.qlink.folder.dto.UpdateFolderRequest
import com.qlink.folder.dto.UpdateFolderResponse
import com.qlink.folder.repository.FolderRepository
import com.qlink.foldermember.domain.MemberRole
import com.qlink.foldermember.repository.FolderMemberRepository
import com.qlink.support.BaseServiceTest
import com.qlink.support.fixture.FolderFixture
import com.qlink.support.fixture.FolderMemberFixture
import com.qlink.support.fixture.RandomFixture
import com.qlink.support.fixture.UserFixture
import com.qlink.support.koinGet
import com.qlink.user.domain.User
import com.qlink.user.repository.UserRepository
import io.kotest.assertions.throwables.shouldThrowWithMessage
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class UpdateFolderServiceTest :
    BaseServiceTest({
        val updateFolderService = koinGet<UpdateFolderService>()
        val folderRepository = koinGet<FolderRepository>()
        val folderMemberRepository = koinGet<FolderMemberRepository>()
        val userRepository = koinGet<UserRepository>()

        Given("폴더 수정 서비스 테스트") {
            lateinit var user: User
            lateinit var otherUser: User
            lateinit var folder: Folder
            lateinit var otherUserFolder: Folder

            beforeTest {
                user = userRepository.insert(UserFixture.createRandomValidUser())
                otherUser = userRepository.insert(UserFixture.createRandomValidUser())
                folder = folderRepository.insert(FolderFixture.createValidUnsharedFolder(user.id!!))
                otherUserFolder = folderRepository.insert(FolderFixture.createValidUnsharedFolder(otherUser.id!!))
            }

            When("본인 폴더 수정을") {
                val request =
                    UpdateFolderRequest(
                        name = RandomFixture.randomSentenceWithMax(100),
                        emoji = RandomFixture.randomEmoji(),
                    )
                val update =
                    suspend {
                        UpdateResult(
                            previous = folder,
                            response = updateFolderService.updateFolder(user.id!!, folder.id!!, request),
                            request = request,
                        )
                    }

                Then("성공한다") {
                    val result = update()
                    val actual = folderRepository.findById(result.previous.id!!)

                    result.response.id shouldBe result.previous.id
                    actual shouldNotBe null
                    actual!!.name shouldBe result.request.name
                    actual.emoji shouldBe result.request.emoji
                    actual.updatedAt shouldNotBe result.previous.updatedAt
                }
            }

            When("소유자가 폴더를 공유 상태로 전환하면") {
                val request =
                    UpdateFolderRequest(
                        name = RandomFixture.randomSentenceWithMax(100),
                        emoji = RandomFixture.randomEmoji(),
                        isShared = true,
                    )
                val update =
                    suspend {
                        updateFolderService.updateFolder(user.id!!, folder.id!!, request)
                    }

                Then("공유 시각과 소유자 멤버가 저장된다") {
                    val response = update()
                    val actual = folderRepository.findById(response.id)
                    val member = folderMemberRepository.findByFolderIdAndUserId(response.id, user.id!!)

                    actual shouldNotBe null
                    actual!!.sharedAt shouldNotBe null
                    member shouldNotBe null
                    member!!.userName shouldBe user.nickname
                    member.role shouldBe MemberRole.OWNER
                }
            }

            When("로그인 사용자가 없으면") {
                val request =
                    UpdateFolderRequest(
                        name = RandomFixture.randomSentenceWithMax(100),
                        emoji = RandomFixture.randomEmoji(),
                    )
                val update =
                    suspend {
                        updateFolderService.updateFolder(RandomFixture.randomId(), folder.id!!, request)
                    }

                Then("예외를 반환한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.FOLDER_OWNER_NOT_FOUND.message) {
                        update()
                    }
                }
            }

            When("없는 폴더 수정을") {
                val request =
                    UpdateFolderRequest(
                        name = RandomFixture.randomSentenceWithMax(100),
                        emoji = RandomFixture.randomEmoji(),
                    )
                val update =
                    suspend {
                        updateFolderService.updateFolder(user.id!!, RandomFixture.randomId(), request)
                    }

                Then("예외를 반환한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.FOLDER_NOT_FOUND.message) {
                        update()
                    }
                }
            }

            When("다른 사용자의 폴더 수정을") {
                val request =
                    UpdateFolderRequest(
                        name = RandomFixture.randomSentenceWithMax(100),
                        emoji = RandomFixture.randomEmoji(),
                    )
                val update =
                    suspend {
                        updateFolderService.updateFolder(user.id!!, otherUserFolder.id!!, request)
                    }

                Then("예외를 반환한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.FOLDER_DIFFERENT_OWNER.message) {
                        update()
                    }
                }
            }

            When("다른 사용자의 폴더를 공유 상태로 전환하면") {
                val request =
                    UpdateFolderRequest(
                        name = RandomFixture.randomSentenceWithMax(100),
                        emoji = RandomFixture.randomEmoji(),
                        isShared = true,
                    )
                val update =
                    suspend {
                        updateFolderService.updateFolder(user.id!!, otherUserFolder.id!!, request)
                    }

                Then("예외를 반환한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.FOLDER_DIFFERENT_OWNER.message) {
                        update()
                    }
                }
            }

            When("같은 이름의 다른 폴더가 이미 있으면") {
                val duplicatedName = RandomFixture.randomSentenceWithMax(100)

                beforeTest {
                    folderRepository.insert(
                        FolderFixture.createFolderWith(
                            ownerId = user.id!!,
                            name = duplicatedName,
                        ),
                    )
                }

                val request =
                    UpdateFolderRequest(
                        name = duplicatedName,
                        emoji = RandomFixture.randomEmoji(),
                    )
                val update =
                    suspend {
                        updateFolderService.updateFolder(user.id!!, folder.id!!, request)
                    }

                Then("예외를 반환한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.FOLDER_DUPLICATE_NAME.message) {
                        update()
                    }
                }
            }

            When("같은 이름으로 자기 자신을 수정하면") {
                val update =
                    suspend {
                        val request =
                            UpdateFolderRequest(
                                name = folder.name,
                                emoji = RandomFixture.randomEmoji(),
                            )
                        val response = updateFolderService.updateFolder(user.id!!, folder.id!!, request)

                        response to request
                    }

                Then("중복으로 보지 않고 성공한다") {
                    val (response, request) = update()
                    val actual = folderRepository.findById(folder.id!!)

                    response.id shouldBe folder.id
                    actual shouldNotBe null
                    actual!!.name shouldBe folder.name
                    actual.emoji shouldBe request.emoji
                }
            }
        }

        Given("소유권이 위임된 공유 폴더의 수정 회귀 검사") {
            lateinit var owner: User
            lateinit var target: User
            lateinit var transferred: Folder
            beforeTest {
                owner = userRepository.insert(UserFixture.createRandomValidUser())
                target = userRepository.insert(UserFixture.createRandomValidUser())
                transferred = folderRepository.insert(FolderFixture.createSharedFolderOf(owner.id!!))
                folderMemberRepository.insertIfAbsent(
                    FolderMemberFixture.createMember(folderId = transferred.id!!, userId = target.id!!),
                )
                folderRepository.update(transferred.delegateTo(target.id!!))
                folderMemberRepository.updateRole(
                    folderId = transferred.id!!,
                    userId = target.id!!,
                    role = MemberRole.OWNER,
                )
            }
            When("새 소유자가 폴더를 수정하면 (회귀)") {
                val request = UpdateFolderRequest(name = RandomFixture.randomSentenceWithMax(100), emoji = RandomFixture.randomEmoji())
                val update =
                    suspend { updateFolderService.updateFolder(loginId = target.id!!, folderId = transferred.id!!, request = request) }
                Then("현재 소유권을 유지하며 변경 내용이 저장된다") {
                    update()
                    folderRepository.findById(transferred.id!!)?.ownerId shouldBe target.id
                    folderRepository.findById(transferred.id!!)?.name shouldBe request.name
                }
            }
        }
    })

private data class UpdateResult(
    val previous: Folder,
    val response: UpdateFolderResponse,
    val request: UpdateFolderRequest,
)
