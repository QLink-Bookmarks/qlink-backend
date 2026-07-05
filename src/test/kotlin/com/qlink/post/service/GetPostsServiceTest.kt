package com.qlink.post.service

import com.qlink.auth.domain.Role
import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import com.qlink.common.scroll.ScrollRequest
import com.qlink.post.domain.Post
import com.qlink.post.domain.PostType
import com.qlink.post.repository.PostRepository
import com.qlink.support.BaseServiceTest
import com.qlink.support.fixture.UserFixture
import com.qlink.support.koinGet
import com.qlink.user.domain.User
import com.qlink.user.repository.UserRepository
import io.kotest.assertions.throwables.shouldThrowWithMessage
import io.kotest.matchers.shouldBe

class GetPostsServiceTest :
    BaseServiceTest({
        val service = koinGet<GetPostsService>()
        val postRepository = koinGet<PostRepository>()
        val userRepository = koinGet<UserRepository>()

        suspend fun seed(): User {
            val author = userRepository.insert(UserFixture.createRandomValidUser())
            postRepository.insert(Post(title = "공지 하나", contents = "c", type = PostType.ANNOUNCEMENT, authorId = author.id!!))
            postRepository.insert(Post(title = "공지 둘", contents = "c", type = PostType.ANNOUNCEMENT, authorId = author.id!!))
            postRepository.insert(Post(title = "피드백 하나", contents = "c", type = PostType.FEEDBACK, authorId = author.id!!))
            return author
        }

        Given("게시글 목록 조회 서비스 테스트") {
            When("타입 없이 GUEST 가 조회하면") {
                Then("기본 ANNOUNCEMENT 만, 작성자/제목 포함으로 반환한다") {
                    val author = seed()

                    val response =
                        service.getPosts(
                            role = Role.GUEST,
                            type = null,
                            query = null,
                            order = "latest",
                            scrollRequest = ScrollRequest(size = 30),
                        )

                    response.contents.size shouldBe 2
                    response.contents.all { it.author == author.nickname } shouldBe true
                    response.contents.first().title shouldBe "공지 둘"
                }
            }

            When("일반 사용자가 FEEDBACK 목록을 조회하면") {
                Then("권한 예외를 반환한다") {
                    seed()

                    shouldThrowWithMessage<BusinessException>(ErrorCode.POST_FEEDBACK_FORBIDDEN.message) {
                        service.getPosts(
                            role = Role.NORMAL,
                            type = "feedback",
                            query = null,
                            order = "latest",
                            scrollRequest = ScrollRequest(size = 30),
                        )
                    }
                }
            }

            When("ADMIN 이 FEEDBACK 을 제목 검색으로 조회하면") {
                Then("해당 피드백만 반환한다") {
                    seed()

                    val response =
                        service.getPosts(
                            role = Role.ADMIN,
                            type = "FEEDBACK",
                            query = "피드백",
                            order = "latest",
                            scrollRequest = ScrollRequest(size = 30),
                        )

                    response.contents.size shouldBe 1
                    response.contents.first().title shouldBe "피드백 하나"
                }
            }
        }
    })
