package link.service

import com.qlink.link.domain.SourceType
import com.qlink.link.dto.CreateLinkRequest
import com.qlink.link.repository.LinkRepository
import com.qlink.link.service.CreateLinkService
import com.qlink.user.repository.UserRepository
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import support.BaseServiceTest
import support.fixture.RandomFixture
import support.fixture.UserFixture
import support.koinGet
import kotlin.random.Random

class CreateLinkServiceTest :
    BaseServiceTest({
        val createLinkService = koinGet<CreateLinkService>()
        val userRepository = koinGet<UserRepository>()
        val linkRepository = koinGet<LinkRepository>()

        Given("링크 생성 서비스 테스트") {
            var user = UserFixture.createRandomValidUser()

            beforeTest {
                user = userRepository.insert(user)
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

                    actual shouldNotBe null
                    actual!!.id shouldBe expected.id
                    actual.url shouldBe request.url
                    actual.title shouldBe request.title
                    actual.summary shouldBe request.summary
                    actual.sourceType shouldBe request.sourceType
                    actual.thumbnailUrl shouldBe request.thumbnailUrl
                    actual.tags shouldBe request.tags
                }
            }

            // TODO: 사용자 및 폴더 확인은 추후 구현
        }
    })
