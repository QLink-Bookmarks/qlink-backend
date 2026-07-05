package com.qlink.post.service

import com.qlink.auth.domain.Role
import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import com.qlink.post.domain.Post
import com.qlink.post.domain.PostImage
import com.qlink.post.domain.PostType
import com.qlink.post.repository.PostRepository
import com.qlink.support.BaseServiceTest
import com.qlink.support.fixture.UserFixture
import com.qlink.support.koinGet
import com.qlink.user.domain.User
import com.qlink.user.repository.UserRepository
import io.kotest.assertions.throwables.shouldThrowWithMessage
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe

class GetPostServiceTest :
    BaseServiceTest({
        val service = koinGet<GetPostService>()
        val postRepository = koinGet<PostRepository>()
        val userRepository = koinGet<UserRepository>()

        Given("게시글 상세 조회 서비스 테스트") {
            lateinit var author: User

            beforeTest {
                author = userRepository.insert(UserFixture.createRandomValidUser())
            }

            When("ANNOUNCEMENT 상세를 GUEST 가 조회하면") {
                Then("작성자/이미지 포함 전체 필드를 반환한다") {
                    val post =
                        postRepository.insert(
                            Post(title = "공지", contents = "내용", type = PostType.ANNOUNCEMENT, authorId = author.id!!),
                        )
                    postRepository.insertImages(listOf(PostImage(postId = post.id!!, url = "https://images.archivelink.app/a.png")))

                    val response = service.getPost(post.id!!, Role.GUEST)

                    response.id shouldBe post.id
                    response.contents shouldBe "내용"
                    response.type shouldBe PostType.ANNOUNCEMENT
                    response.author shouldBe author.nickname
                    response.imageUrls shouldContainExactly listOf("https://images.archivelink.app/a.png")
                }
            }

            When("FEEDBACK 상세를 일반 사용자가 조회하면") {
                Then("권한 예외를 반환한다") {
                    val post =
                        postRepository.insert(
                            Post(title = "피드백", contents = "내용", type = PostType.FEEDBACK, authorId = author.id!!),
                        )

                    shouldThrowWithMessage<BusinessException>(ErrorCode.POST_FEEDBACK_FORBIDDEN.message) {
                        service.getPost(post.id!!, Role.NORMAL)
                    }
                }
            }

            When("FEEDBACK 상세를 관리자가 조회하면") {
                Then("이미지 조회 없이 상세를 반환한다") {
                    val post =
                        postRepository.insert(
                            Post(title = "피드백", contents = "내용", type = PostType.FEEDBACK, authorId = author.id!!),
                        )

                    val response = service.getPost(post.id!!, Role.ADMIN)

                    response.type shouldBe PostType.FEEDBACK
                    response.imageUrls shouldContainExactly emptyList()
                }
            }

            When("존재하지 않는 게시글을 조회하면") {
                Then("예외를 반환한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.POST_NOT_FOUND.message) {
                        service.getPost(987654321L, Role.ADMIN)
                    }
                }
            }
        }
    })
