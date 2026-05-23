package com.qlink.link.service

import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import com.qlink.folder.domain.Folder
import com.qlink.folder.repository.FolderRepository
import com.qlink.link.domain.SourceType
import com.qlink.link.dto.CreateLinkRequest
import com.qlink.link.repository.LinkRepository
import com.qlink.link.service.CreateLinkService
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
import kotlin.random.Random

class CreateLinkServiceTest :
    BaseServiceTest({
        val createLinkService = koinGet<CreateLinkService>()
        val userRepository = koinGet<UserRepository>()
        val linkRepository = koinGet<LinkRepository>()
        val folderRepository = koinGet<FolderRepository>()

        Given("링크 생성 서비스 테스트") {
            lateinit var user: User
            lateinit var folder: Folder

            beforeTest {
                user = userRepository.insert(UserFixture.createRandomValidUser())
                folder = folderRepository.insert(FolderFixture.createValidUnsharedFolder(user.id!!))
            }

            When("링크 생성을") {
                val request =
                    CreateLinkRequest(
                        url = RandomFixture.randomUrl(),
                        title = RandomFixture.randomSentenceWithMax(300),
                        summary = RandomFixture.randomSentenceWithMax(1_000),
                        sourceType = SourceType.entries[Random.nextInt(SourceType.entries.size)],
                        thumbnailUrl = RandomFixture.randomUrl(),
                        tags = RandomFixture.randomSentenceList(),
                    )
                val expected = createLinkService.createLink(user.id!!, request)

                Then("성공한다") {
                    val actual = linkRepository.findById(expected.id)
                    val expectedTags = request.tags.distinct()

                    actual shouldNotBe null
                    actual!!.id shouldBe expected.id
                    actual.url shouldBe request.url
                    actual.title shouldBe request.title
                    actual.summary shouldBe request.summary
                    actual.sourceType shouldBe request.sourceType
                    actual.thumbnailUrl shouldBe request.thumbnailUrl
                    actual.tags shouldBe expectedTags
                }
            }

            When("로그인 사용자가 없으면") {
                val invalidUserId = RandomFixture.randomId()
                val request =
                    CreateLinkRequest(
                        url = RandomFixture.randomUrl(),
                        title = RandomFixture.randomSentenceWithMax(300),
                        summary = RandomFixture.randomSentenceWithMax(1_000),
                        sourceType = SourceType.entries[Random.nextInt(SourceType.entries.size)],
                        thumbnailUrl = RandomFixture.randomUrl(),
                        tags = RandomFixture.randomSentenceList(),
                    )
                val create =
                    suspend {
                        createLinkService.createLink(invalidUserId, request)
                    }

                Then("예외를 반환한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.LINK_OWNER_NOT_FOUND.message) {
                        create()
                    }
                }
            }

            When("폴더 아이디가 요청에 포함됐지만 폴더가 없으면") {
                val invalidFolderId = RandomFixture.randomId()
                val request =
                    CreateLinkRequest(
                        folderId = invalidFolderId,
                        url = RandomFixture.randomUrl(),
                        title = RandomFixture.randomSentenceWithMax(300),
                        summary = RandomFixture.randomSentenceWithMax(1_000),
                        sourceType = SourceType.entries[Random.nextInt(SourceType.entries.size)],
                        thumbnailUrl = RandomFixture.randomUrl(),
                        tags = RandomFixture.randomSentenceList(),
                    )
                val create =
                    suspend {
                        createLinkService.createLink(user.id!!, request)
                    }

                Then("예외를 반환한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.LINK_FOLDER_NOT_FOUND.message) {
                        create()
                    }
                }
            }
        }
    })
