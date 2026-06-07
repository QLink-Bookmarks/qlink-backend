package com.qlink.folder.service

import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import com.qlink.folder.dto.CreateFolderRequest
import com.qlink.folder.repository.FolderRepository
import com.qlink.foldermember.repository.FolderMemberRepository
import com.qlink.support.BaseServiceTest
import com.qlink.support.fixture.FolderFixture
import com.qlink.support.fixture.RandomFixture
import com.qlink.support.fixture.UserFixture
import com.qlink.support.koinGet
import com.qlink.user.domain.User
import com.qlink.user.repository.UserRepository
import io.kotest.assertions.throwables.shouldThrowWithMessage
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class CreateFolderServiceTest :
    BaseServiceTest({
        val createFolderService = koinGet<CreateFolderService>()
        val folderRepository = koinGet<FolderRepository>()
        val folderMemberRepository = koinGet<FolderMemberRepository>()
        val userRepository = koinGet<UserRepository>()

        Given("폴더 생성 서비스 테스트") {
            lateinit var user: User

            beforeTest {
                user = userRepository.insert(UserFixture.createRandomValidUser())
            }

            When("유효한 입력으로 폴더를 생성하면") {
                val request =
                    CreateFolderRequest(
                        name = RandomFixture.randomSentenceWithMax(100),
                        emoji = RandomFixture.randomEmoji(),
                        isShared = true,
                    )
                val create = suspend { createFolderService.createFolder(user.id!!, request) }

                Then("폴더가 저장된다") {
                    val response = create()
                    val actual = folderRepository.findById(response.id)

                    actual shouldNotBe null
                    actual!!.ownerId shouldBe user.id!!
                    actual.name shouldBe request.name
                    actual.emoji shouldBe request.emoji
                    actual.sharedAt shouldNotBe null

                    val member = folderMemberRepository.findByFolderIdAndUserId(response.id, user.id!!)
                    member shouldNotBe null
                    member!!.userName shouldBe user.nickname
                    member.role shouldBe "OWNER"
                }
            }

            When("이모지가 없으면") {
                val request =
                    CreateFolderRequest(
                        name = RandomFixture.randomSentenceWithMax(100),
                        isShared = false,
                    )
                val create = suspend { createFolderService.createFolder(user.id!!, request) }

                Then("기본 이모지가 저장된다") {
                    val response = create()
                    val actual = folderRepository.findById(response.id)

                    actual shouldNotBe null
                    actual!!.emoji shouldNotBe null
                    actual.sharedAt shouldBe null
                }
            }

            When("로그인 사용자가 없으면") {
                val request =
                    CreateFolderRequest(
                        name = RandomFixture.randomSentenceWithMax(100),
                        emoji = RandomFixture.randomEmoji(),
                    )
                val create = suspend { createFolderService.createFolder(RandomFixture.randomId(), request) }

                Then("예외를 반환한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.FOLDER_OWNER_NOT_FOUND.message) {
                        create()
                    }
                }
            }

            When("같은 이름의 폴더가 이미 있으면") {
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
                    CreateFolderRequest(
                        name = duplicatedName,
                        emoji = RandomFixture.randomEmoji(),
                    )
                val create = suspend { createFolderService.createFolder(user.id!!, request) }

                Then("예외를 반환한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.FOLDER_DUPLICATE_NAME.message) {
                        create()
                    }
                }
            }
        }
    })
