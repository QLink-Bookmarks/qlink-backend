package com.qlink.bookmarkimport.domain

import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import com.qlink.support.fixture.ImportFixture
import com.qlink.support.fixture.RandomFixture
import io.kotest.assertions.throwables.shouldThrowWithMessage
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldHaveLength
import java.text.Normalizer
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

class ImportSourceTest :
    BehaviorSpec({
        Given("가져오기 원본을 만들 때") {
            When("링크 1개 이상, 링크·폴더 key가 각각 겹치지 않고, folderKey가 모두 폴더 항목을 가리키면") {
                val folder = ImportFixture.createSourceFolder()
                val links = listOf(ImportFixture.createSourceLink(folderKey = folder.key), ImportFixture.createSourceLink())
                val source = ImportSource(folders = listOf(folder), links = links)

                Then("원본이 만들어진다") {
                    source.folders.map { it.key } shouldContainExactly listOf(folder.key)
                    source.links.map { it.key } shouldContainExactly links.map { it.key }
                }
            }

            When("링크가 없으면") {
                val create = { ImportFixture.createSource(links = emptyList()) }

                Then("예외가 발생한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.IMPORT_LINKS_EMPTY.message) {
                        create()
                    }
                }
            }

            When("링크가 정확히 3,000개면") {
                val source = ImportFixture.createSource(links = ImportFixture.createSourceLinks(ImportSource.LINKS_MAX_COUNT))

                Then("원본이 만들어진다") {
                    source.links shouldHaveSize ImportSource.LINKS_MAX_COUNT
                }
            }

            When("링크가 3,001개면") {
                val create = { ImportFixture.createSource(links = ImportFixture.createSourceLinks(ImportSource.LINKS_MAX_COUNT + 1)) }

                Then("예외가 발생한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.IMPORT_LINKS_OVER_MAX.message) {
                        create()
                    }
                }
            }

            When("폴더 항목이 없으면") {
                val source = ImportFixture.createSource(folders = emptyList())

                Then("원본이 만들어진다") {
                    source.folders.shouldBeEmpty()
                }
            }

            When("폴더 항목이 정확히 3,000개면") {
                val source = ImportFixture.createSource(folders = ImportFixture.createSourceFolders(ImportSource.FOLDERS_MAX_COUNT))

                Then("원본이 만들어진다") {
                    source.folders shouldHaveSize ImportSource.FOLDERS_MAX_COUNT
                }
            }

            When("폴더 항목이 3,001개면") {
                val create = { ImportFixture.createSource(folders = ImportFixture.createSourceFolders(ImportSource.FOLDERS_MAX_COUNT + 1)) }

                Then("예외가 발생한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.IMPORT_FOLDERS_OVER_MAX.message) {
                        create()
                    }
                }
            }

            When("링크 key가 겹치면") {
                val key = ImportFixture.randomKey()
                val create = {
                    ImportFixture.createSource(
                        links = listOf(ImportFixture.createSourceLink(key = key), ImportFixture.createSourceLink(key = key)),
                    )
                }

                Then("예외가 발생한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.IMPORT_KEY_DUPLICATED.message) {
                        create()
                    }
                }
            }

            When("폴더 key가 겹치면") {
                val key = ImportFixture.randomKey()
                val create = {
                    ImportFixture.createSource(
                        folders = listOf(ImportFixture.createSourceFolder(key = key), ImportFixture.createSourceFolder(key = key)),
                    )
                }

                Then("예외가 발생한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.IMPORT_KEY_DUPLICATED.message) {
                        create()
                    }
                }
            }

            When("링크 key와 폴더 key만 같은 값이면") {
                val key = ImportFixture.randomKey()
                val source =
                    ImportFixture.createSource(
                        folders = listOf(ImportFixture.createSourceFolder(key = key)),
                        links = listOf(ImportFixture.createSourceLink(key = key)),
                    )

                Then("원본이 만들어진다") {
                    source.folders.single().key shouldBe key
                    source.links.single().key shouldBe key
                }
            }

            When("폴더 이름이 공백뿐이면") {
                val create = { ImportFixture.createSource(folders = listOf(ImportFixture.createSourceFolder(name = "   "))) }

                Then("예외가 발생한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.IMPORT_FOLDER_NAME_BLANK.message) {
                        create()
                    }
                }
            }

            When("폴더 이름이 정확히 100자(UTF-16)면") {
                val name = RandomFixture.randomFixedSentence(ImportSource.FOLDER_NAME_MAX_LENGTH)
                val source = ImportFixture.createSource(folders = listOf(ImportFixture.createSourceFolder(name = name)))

                Then("원본이 만들어진다") {
                    source.folders.single().name shouldBe name
                }
            }

            When("NFC로 정규화한 폴더 이름이 정확히 100자면") {
                val decomposed = Normalizer.normalize("가".repeat(ImportSource.FOLDER_NAME_MAX_LENGTH), Normalizer.Form.NFD)
                val source = ImportFixture.createSource(folders = listOf(ImportFixture.createSourceFolder(name = decomposed)))

                Then("원본이 만들어진다") {
                    decomposed.length shouldBe ImportSource.FOLDER_NAME_MAX_LENGTH * 2
                    source.folders.single().name shouldBe decomposed
                }
            }

            When("폴더 이름이 101자면") {
                val name = RandomFixture.randomFixedSentence(ImportSource.FOLDER_NAME_MAX_LENGTH + 1)
                val create = { ImportFixture.createSource(folders = listOf(ImportFixture.createSourceFolder(name = name))) }

                Then("예외가 발생한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.IMPORT_FOLDER_NAME_OVER_MAX.message) {
                        create()
                    }
                }
            }

            When("folderKey가 없는 폴더 key를 가리키면") {
                val create = {
                    ImportFixture.createSource(
                        folders = listOf(ImportFixture.createSourceFolder()),
                        links = listOf(ImportFixture.createSourceLink(folderKey = ImportFixture.randomKey())),
                    )
                }

                Then("예외가 발생한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.IMPORT_FOLDER_KEY_NOT_FOUND.message) {
                        create()
                    }
                }
            }

            When("folderKey가 null이면") {
                val source =
                    ImportFixture.createSource(
                        folders = listOf(ImportFixture.createSourceFolder()),
                        links = listOf(ImportFixture.createSourceLink(folderKey = null)),
                    )
                val folders = source.foldersOf(source.links)

                Then("그 링크는 미분류로 담긴다") {
                    source.links.single().folderKey shouldBe null
                    folders.shouldBeEmpty()
                }
            }
        }

        Given("내 링크 URL 목록과 미리보기 링크 항목") {
            val host = "example-${RandomFixture.randomId()}.com"

            When("내 링크에 없는 URL이면") {
                val preview =
                    ImportFixture
                        .createSource(links = listOf(ImportFixture.createSourceLink(url = "https://$host/new")))
                        .previewLinks(listOf("https://$host/saved"))

                Then("NEW") {
                    preview.single().status shouldBe ImportPreviewLinkStatus.NEW
                }
            }

            When("스킴·호스트 대소문자, 기본 포트, fragment, 추적 query, query 키 순서, 끝 /만 다르면") {
                val saved = "https://$host/path?a=1&b=2"
                val tracking = "utm_source=x&utm_medium=y&fbclid=1&gclid=2&igshid=3&mc_cid=4&mc_eid=5"
                val requested = "HTTPS://${host.uppercase()}:443/path/?b=2&$tracking&a=1#section"
                val preview =
                    ImportFixture
                        .createSource(links = listOf(ImportFixture.createSourceLink(url = requested)))
                        .previewLinks(listOf(saved))

                Then("SAVED") {
                    preview.single().status shouldBe ImportPreviewLinkStatus.SAVED
                }
            }

            When("추적 query가 아닌 query 값이 다르면") {
                val preview =
                    ImportFixture
                        .createSource(links = listOf(ImportFixture.createSourceLink(url = "https://$host/path?a=2")))
                        .previewLinks(listOf("https://$host/path?a=1"))

                Then("NEW") {
                    preview.single().status shouldBe ImportPreviewLinkStatus.NEW
                }
            }

            When("요청 안에서 앞선 링크와 정규화 URL이 같으면") {
                val links =
                    listOf(
                        ImportFixture.createSourceLink(url = "https://$host/same"),
                        ImportFixture.createSourceLink(url = "HTTPS://${host.uppercase()}:443/same/#top"),
                    )
                val preview = ImportFixture.createSource(links = links).previewLinks(emptyList())

                Then("뒤 링크만 IN_FILE_DUPLICATE") {
                    preview.map { it.key } shouldContainExactly links.map { it.key }
                    preview.map { it.status } shouldContainExactly
                        listOf(ImportPreviewLinkStatus.NEW, ImportPreviewLinkStatus.IN_FILE_DUPLICATE)
                }
            }

            When("정규화할 수 없는 같은 URL이 내 링크와 요청에 있으면") {
                val broken = "https://exa mple ${RandomFixture.randomId()}"
                val links = List(2) { ImportFixture.createSourceLink(url = broken) }
                val preview = ImportFixture.createSource(links = links).previewLinks(listOf(broken))

                Then("중복으로 보지 않고 모두 NEW") {
                    preview.map { it.status } shouldContainExactly
                        listOf(ImportPreviewLinkStatus.NEW, ImportPreviewLinkStatus.NEW)
                }
            }
        }

        Given("내 링크 URL 목록과 시작 링크 항목") {
            fun newLinksOf(
                links: List<ImportSourceLink>,
                myUrls: List<String> = emptyList(),
                failedKeys: Set<String> = emptySet(),
            ): List<ImportSourceLink> = ImportFixture.createSource(links = links).newLinks(failedKeys, myUrls)

            When("내 링크와 정규화 URL이 같으면") {
                val saved = ImportFixture.randomUniqueUrl()
                val other = ImportFixture.createSourceLink()
                val links =
                    newLinksOf(links = listOf(ImportFixture.createSourceLink(url = "$saved/#fragment"), other), myUrls = listOf(saved))

                Then("빠진다") {
                    links.map { it.key } shouldContainExactly listOf(other.key)
                }
            }

            When("요청 안에서 앞선 링크와 정규화 URL이 같으면") {
                val url = ImportFixture.randomUniqueUrl()
                val first = ImportFixture.createSourceLink(url = url)
                val links = newLinksOf(links = listOf(first, ImportFixture.createSourceLink(url = "$url?utm_source=x")))

                Then("뒤 링크가 빠진다") {
                    links.map { it.key } shouldContainExactly listOf(first.key)
                }
            }

            When("앞선 같은 URL 링크가 실패 key에 있으면") {
                val url = ImportFixture.randomUniqueUrl()
                val failed = ImportFixture.createSourceLink(url = url)
                val valid = ImportFixture.createSourceLink(url = url)
                val links = newLinksOf(links = listOf(failed, valid), failedKeys = setOf(failed.key))

                Then("뒤 링크는 남는다") {
                    links.map { it.key } shouldContainExactly listOf(valid.key)
                }
            }
        }

        Given("원본 링크 항목") {
            val now = Clock.System.now()

            When("URL이 정확히 2,048자면") {
                val prefix = "https://${ImportFixture.randomKey()}.com/"
                val url = prefix + "a".repeat(ImportSource.LINK_URL_MAX_LENGTH - prefix.length)
                val reason = ImportFixture.createSourceLink(url = url).failureReason()

                Then("실패 아님") {
                    url shouldHaveLength ImportSource.LINK_URL_MAX_LENGTH
                    reason shouldBe null
                }
            }

            When("URL이 2,049자면") {
                val prefix = "https://${ImportFixture.randomKey()}.com/"
                val url = prefix + "a".repeat(ImportSource.LINK_URL_MAX_LENGTH + 1 - prefix.length)
                val reason = ImportFixture.createSourceLink(url = url).failureReason()

                Then("URL_INVALID") {
                    reason shouldBe ImportFailureReason.URL_INVALID
                }
            }

            When("제목이 공백뿐이면") {
                val reason = ImportFixture.createSourceLink(title = "  ").failureReason()

                Then("TITLE_INVALID") {
                    reason shouldBe ImportFailureReason.TITLE_INVALID
                }
            }

            When("제목이 정확히 300자면") {
                val title = RandomFixture.randomFixedSentence(ImportSource.LINK_TITLE_MAX_LENGTH)
                val reason = ImportFixture.createSourceLink(title = title).failureReason()

                Then("실패 아님") {
                    reason shouldBe null
                }
            }

            When("제목이 301자면") {
                val reason =
                    ImportFixture
                        .createSourceLink(title = RandomFixture.randomFixedSentence(ImportSource.LINK_TITLE_MAX_LENGTH + 1))
                        .failureReason()

                Then("TITLE_INVALID") {
                    reason shouldBe ImportFailureReason.TITLE_INVALID
                }
            }

            When("createdAt이 과거면") {
                val createdAt = RandomFixture.randomPastInstant()
                val resolved = ImportFixture.createSourceLink(createdAt = createdAt).createdAtOr(now)

                Then("그 값") {
                    resolved shouldBe createdAt
                }
            }

            When("createdAt이 null이면") {
                val resolved = ImportFixture.createSourceLink(createdAt = null).createdAtOr(now)

                Then("저장 시각") {
                    resolved shouldBe now
                }
            }

            When("createdAt이 저장 시각보다 미래면") {
                val resolved = ImportFixture.createSourceLink(createdAt = now + 1.days).createdAtOr(now)

                Then("저장 시각") {
                    resolved shouldBe now
                }
            }
        }

        Given("원본 폴더 항목") {
            val now = Clock.System.now()

            When("createdAt이 저장 시각보다 미래면") {
                val resolved = ImportFixture.createSourceFolder(createdAt = now + 1.days).createdAtOr(now)

                Then("저장 시각") {
                    resolved shouldBe now
                }
            }
        }
    })
