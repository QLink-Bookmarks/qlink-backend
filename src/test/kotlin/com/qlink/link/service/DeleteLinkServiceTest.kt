package com.qlink.link.service

import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import com.qlink.link.domain.Link
import com.qlink.link.repository.LinkRepository
import com.qlink.support.BaseServiceTest
import com.qlink.support.fixture.LinkFixture
import com.qlink.support.fixture.RandomFixture
import com.qlink.support.fixture.UserFixture
import com.qlink.support.koinGet
import com.qlink.user.domain.User
import com.qlink.user.repository.UserRepository
import io.kotest.assertions.throwables.shouldNotThrow
import io.kotest.assertions.throwables.shouldThrowWithMessage
import io.kotest.matchers.shouldBe

class DeleteLinkServiceTest :
    BaseServiceTest({
        val deleteLinkService = koinGet<DeleteLinkService>()
        val getLinkDetailService = koinGet<GetLinkDetailService>()
        val userRepository = koinGet<UserRepository>()
        val linkRepository = koinGet<LinkRepository>()

        Given("링크 삭제 서비스 테스트") {
            lateinit var user: User
            lateinit var otherUser: User
            lateinit var link: Link
            lateinit var otherUserLink: Link

            beforeTest {
                user = userRepository.insert(UserFixture.createRandomValidUser())
                otherUser = userRepository.insert(UserFixture.createRandomValidUser())
                link =
                    linkRepository.insert(
                        LinkFixture.createRandomLinkOf(
                            ownerId = user.id!!,
                        ),
                    )
                otherUserLink =
                    linkRepository.insert(
                        LinkFixture.createRandomLinkOf(
                            ownerId = otherUser.id!!,
                        ),
                    )
            }

            When("본인 링크 삭제를") {
                val delete =
                    suspend {
                        deleteLinkService.deleteLink(user.id!!, link.id!!)
                    }

                Then("성공하고 상세 조회에서 제외된다") {
                    val linkId = link.id!!

                    shouldNotThrow<BusinessException> {
                        delete()
                    }

                    linkRepository.findById(linkId) shouldBe null

                    shouldThrowWithMessage<BusinessException>(ErrorCode.LINK_NOT_FOUND.message) {
                        getLinkDetailService.getLinkDetail(user.id!!, linkId)
                    }
                }
            }

            When("로그인 사용자가 없으면") {
                val invalidUserId = RandomFixture.randomId()
                val delete =
                    suspend {
                        deleteLinkService.deleteLink(invalidUserId, link.id!!)
                    }

                Then("예외를 반환한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.LINK_OWNER_NOT_FOUND.message) {
                        delete()
                    }
                }
            }

            When("없는 링크 삭제를") {
                val invalidLinkId = RandomFixture.randomId()
                val delete =
                    suspend {
                        deleteLinkService.deleteLink(user.id!!, invalidLinkId)
                    }

                Then("성공한다") {
                    shouldNotThrow<BusinessException> {
                        delete()
                    }
                }
            }

            When("다른 사용자의 링크 삭제를") {
                val delete =
                    suspend {
                        deleteLinkService.deleteLink(user.id!!, otherUserLink.id!!)
                    }

                Then("예외를 반환한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.LINK_DIFFERENT_OWNER.message) {
                        delete()
                    }
                }
            }
        }
    })
