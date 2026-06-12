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
import io.kotest.assertions.throwables.shouldThrowWithMessage
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

class SetLinkFavoriteServiceTest :
    BaseServiceTest({
        val setLinkFavoriteService = koinGet<SetLinkFavoriteService>()
        val userRepository = koinGet<UserRepository>()
        val linkRepository = koinGet<LinkRepository>()

        Given("링크 바로가기 설정 서비스 테스트") {
            lateinit var user: User
            lateinit var otherUser: User

            beforeTest {
                user = userRepository.insert(UserFixture.createRandomValidUser())
                otherUser = userRepository.insert(UserFixture.createRandomValidUser())
            }

            When("바로가기가 없는 본인 링크에 isFavorite=true로 설정하면") {
                val link = linkRepository.insert(LinkFixture.createRandomLinkOf(ownerId = user.id!!))
                setLinkFavoriteService.setFavorite(user.id!!, link.id!!, true)
                val actual = linkRepository.findById(link.id!!)

                Then("favoriteAt가 추가된다") {
                    actual!!.favoriteAt.shouldNotBeNull()
                }
            }

            When("바로가기인 본인 링크에 isFavorite=false로 설정하면") {
                val link =
                    linkRepository.insert(
                        LinkFixture.createRandomLinkOf(
                            ownerId = user.id!!,
                            favoriteAt = RandomFixture.randomPastInstant(),
                        ),
                    )
                setLinkFavoriteService.setFavorite(user.id!!, link.id!!, false)
                val actual = linkRepository.findById(link.id!!)

                Then("favoriteAt가 제거된다") {
                    actual!!.favoriteAt shouldBe null
                }
            }

            When("이미 바로가기인 본인 링크에 isFavorite=true로 설정하면") {
                val link =
                    linkRepository.insert(
                        LinkFixture.createRandomLinkOf(
                            ownerId = user.id!!,
                            favoriteAt = RandomFixture.randomPastInstant(),
                        ),
                    )
                setLinkFavoriteService.setFavorite(user.id!!, link.id!!, true)
                val actual = linkRepository.findById(link.id!!)

                Then("기존 favoriteAt가 유지된다") {
                    actual!!.favoriteAt shouldBe link.favoriteAt
                }
            }

            When("isFavorite가 null이면") {
                val link =
                    linkRepository.insert(
                        LinkFixture.createRandomLinkOf(
                            ownerId = user.id!!,
                            favoriteAt = RandomFixture.randomPastInstant(),
                        ),
                    )
                setLinkFavoriteService.setFavorite(user.id!!, link.id!!, null)
                val actual = linkRepository.findById(link.id!!)

                Then("변경 없이 기존 상태가 유지된다") {
                    actual!!.favoriteAt shouldBe link.favoriteAt
                }
            }

            When("로그인 사용자가 없으면") {
                val link: Link = linkRepository.insert(LinkFixture.createRandomLinkOf(ownerId = user.id!!))
                val setFavorite =
                    suspend {
                        setLinkFavoriteService.setFavorite(RandomFixture.randomId(), link.id!!, true)
                    }

                Then("예외를 반환한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.LINK_OWNER_NOT_FOUND.message) {
                        setFavorite()
                    }
                }
            }

            When("없는 링크에 설정하면") {
                val setFavorite =
                    suspend {
                        setLinkFavoriteService.setFavorite(user.id!!, RandomFixture.randomId(), true)
                    }

                Then("예외를 반환한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.LINK_NOT_FOUND.message) {
                        setFavorite()
                    }
                }
            }

            When("다른 사용자의 링크에 설정하면") {
                val otherUserLink = linkRepository.insert(LinkFixture.createRandomLinkOf(ownerId = otherUser.id!!))
                val setFavorite =
                    suspend {
                        setLinkFavoriteService.setFavorite(user.id!!, otherUserLink.id!!, true)
                    }

                Then("예외를 반환한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.LINK_DIFFERENT_OWNER.message) {
                        setFavorite()
                    }
                }
            }
        }
    })
