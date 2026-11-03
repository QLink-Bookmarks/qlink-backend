package com.qlink.bookmarkimport.domain

import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import com.qlink.support.fixture.FolderFixture
import com.qlink.support.fixture.ImportFixture
import com.qlink.support.fixture.RandomFixture
import io.kotest.assertions.throwables.shouldThrowWithMessage
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldHaveLength
import java.text.Normalizer
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

class ImportSourceTest :
    BehaviorSpec({
        fun uniqueName(): String = "폴더 ${RandomFixture.randomId()}"

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
                val plan = source.plan(RandomFixture.randomId(), emptyList(), emptyList(), Clock.System.now())

                Then("그 링크는 미분류로 담긴다") {
                    source.links.single().folderKey shouldBe null
                    plan.links.single().folder shouldBe null
                }
            }
        }

        Given("내 폴더 목록과 미리보기 폴더 항목") {
            val ownerId = RandomFixture.randomId()

            When("이름이 내 공유 아닌 폴더와 같으면") {
                val mine = FolderFixture.createFolderWith(ownerId = ownerId, name = uniqueName())
                val folder = ImportFixture.createSourceFolder(name = mine.name)
                val preview = ImportFixture.createSource(folders = listOf(folder)).preview(listOf(mine), emptyList())

                Then("EXISTING, 그 폴더의 folderId, suggestedName은 그 폴더 이름") {
                    val actual = preview.folders.single()
                    actual.key shouldBe folder.key
                    actual.matchType shouldBe ImportFolderMatchType.EXISTING
                    actual.folderId shouldBe mine.id
                    actual.suggestedName shouldBe mine.name
                }
            }

            When("앞뒤 공백만 다르면") {
                val mine = FolderFixture.createFolderWith(ownerId = ownerId, name = uniqueName())
                val preview =
                    ImportFixture
                        .createSource(folders = listOf(ImportFixture.createSourceFolder(name = "  ${mine.name}\t")))
                        .preview(listOf(mine), emptyList())

                Then("EXISTING") {
                    preview.folders.single().matchType shouldBe ImportFolderMatchType.EXISTING
                }
            }

            When("NFC 정규화 전후로만 다르면(조합형·완성형 한글)") {
                val mine = FolderFixture.createFolderWith(ownerId = ownerId, name = uniqueName())
                val decomposed = Normalizer.normalize(mine.name, Normalizer.Form.NFD)
                val preview =
                    ImportFixture
                        .createSource(folders = listOf(ImportFixture.createSourceFolder(name = decomposed)))
                        .preview(listOf(mine), emptyList())

                Then("EXISTING") {
                    decomposed shouldNotBe mine.name
                    preview.folders.single().matchType shouldBe ImportFolderMatchType.EXISTING
                }
            }

            When("대소문자만 다르면") {
                val mine = FolderFixture.createFolderWith(ownerId = ownerId, name = "folder-${RandomFixture.randomId()}")
                val preview =
                    ImportFixture
                        .createSource(folders = listOf(ImportFixture.createSourceFolder(name = mine.name.uppercase())))
                        .preview(listOf(mine), emptyList())

                Then("NEW") {
                    preview.folders.single().matchType shouldBe ImportFolderMatchType.NEW
                }
            }

            When("이름이 내 공유 폴더와 같으면") {
                val mine = FolderFixture.createSharedFolderOf(ownerId = ownerId, name = uniqueName())
                val preview =
                    ImportFixture
                        .createSource(folders = listOf(ImportFixture.createSourceFolder(name = mine.name)))
                        .preview(listOf(mine), emptyList())

                Then("SHARED_CONFLICT, folderId는 null, suggestedName은 이름 2") {
                    val actual = preview.folders.single()
                    actual.matchType shouldBe ImportFolderMatchType.SHARED_CONFLICT
                    actual.folderId shouldBe null
                    actual.suggestedName shouldBe "${mine.name} 2"
                }
            }

            When("이름 2가 내 폴더 이름으로 이미 있으면") {
                val shared = FolderFixture.createSharedFolderOf(ownerId = ownerId, name = uniqueName())
                val numbered = FolderFixture.createFolderWith(ownerId = ownerId, name = "${shared.name} 2")
                val preview =
                    ImportFixture
                        .createSource(folders = listOf(ImportFixture.createSourceFolder(name = shared.name)))
                        .preview(listOf(shared, numbered), emptyList())

                Then("suggestedName은 이름 3") {
                    preview.folders.single().suggestedName shouldBe "${shared.name} 3"
                }
            }

            When("이름 2가 요청의 다른 폴더 이름으로 이미 있으면") {
                val shared = FolderFixture.createSharedFolderOf(ownerId = ownerId, name = uniqueName())
                val conflict = ImportFixture.createSourceFolder(name = shared.name)
                val other = ImportFixture.createSourceFolder(name = "${shared.name} 2")
                val preview = ImportFixture.createSource(folders = listOf(conflict, other)).preview(listOf(shared), emptyList())

                Then("suggestedName은 이름 3") {
                    preview.folders.single { it.key == conflict.key }.suggestedName shouldBe "${shared.name} 3"
                }
            }

            When("같은 이름의 SHARED_CONFLICT 항목이 둘이면") {
                val shared = FolderFixture.createSharedFolderOf(ownerId = ownerId, name = uniqueName())
                val folders = List(2) { ImportFixture.createSourceFolder(name = shared.name) }
                val preview = ImportFixture.createSource(folders = folders).preview(listOf(shared), emptyList())

                Then("두 suggestedName이 서로 다르다") {
                    val (first, second) = preview.folders
                    first.matchType shouldBe ImportFolderMatchType.SHARED_CONFLICT
                    second.matchType shouldBe ImportFolderMatchType.SHARED_CONFLICT
                    first.suggestedName shouldNotBe second.suggestedName
                }
            }

            When("100자 이름이 SHARED_CONFLICT면") {
                val name = RandomFixture.randomFixedSentence(ImportSource.FOLDER_NAME_MAX_LENGTH)
                val shared = FolderFixture.createSharedFolderOf(ownerId = ownerId, name = name)
                val preview =
                    ImportFixture
                        .createSource(folders = listOf(ImportFixture.createSourceFolder(name = name)))
                        .preview(listOf(shared), emptyList())

                Then("suggestedName은 원래 이름 끝을 줄여 번호를 붙인 100자다") {
                    val suggestedName = preview.folders.single().suggestedName
                    suggestedName shouldHaveLength ImportSource.FOLDER_NAME_MAX_LENGTH
                    suggestedName shouldBe "${name.take(ImportSource.FOLDER_NAME_MAX_LENGTH - 2)} 2"
                }
            }

            When("일치하는 폴더가 없으면") {
                val name = uniqueName()
                val preview =
                    ImportFixture
                        .createSource(folders = listOf(ImportFixture.createSourceFolder(name = " $name ")))
                        .preview(listOf(FolderFixture.createFolderWith(ownerId = ownerId, name = uniqueName())), emptyList())

                Then("NEW, folderId는 null, suggestedName은 앞뒤 공백을 뗀 원래 이름") {
                    val actual = preview.folders.single()
                    actual.matchType shouldBe ImportFolderMatchType.NEW
                    actual.folderId shouldBe null
                    actual.suggestedName shouldBe name
                }
            }
        }

        Given("내 링크 URL 목록과 미리보기 링크 항목") {
            val host = "example-${RandomFixture.randomId()}.com"

            When("내 링크에 없는 URL이면") {
                val preview =
                    ImportFixture
                        .createSource(links = listOf(ImportFixture.createSourceLink(url = "https://$host/new")))
                        .preview(emptyList(), listOf("https://$host/saved"))

                Then("NEW") {
                    preview.links.single().status shouldBe ImportPreviewLinkStatus.NEW
                }
            }

            When("스킴·호스트 대소문자, 기본 포트, fragment, 추적 query, query 키 순서, 끝 /만 다르면") {
                val saved = "https://$host/path?a=1&b=2"
                val tracking = "utm_source=x&utm_medium=y&fbclid=1&gclid=2&igshid=3&mc_cid=4&mc_eid=5"
                val requested = "HTTPS://${host.uppercase()}:443/path/?b=2&$tracking&a=1#section"
                val preview =
                    ImportFixture
                        .createSource(links = listOf(ImportFixture.createSourceLink(url = requested)))
                        .preview(emptyList(), listOf(saved))

                Then("SAVED") {
                    preview.links.single().status shouldBe ImportPreviewLinkStatus.SAVED
                }
            }

            When("추적 query가 아닌 query 값이 다르면") {
                val preview =
                    ImportFixture
                        .createSource(links = listOf(ImportFixture.createSourceLink(url = "https://$host/path?a=2")))
                        .preview(emptyList(), listOf("https://$host/path?a=1"))

                Then("NEW") {
                    preview.links.single().status shouldBe ImportPreviewLinkStatus.NEW
                }
            }

            When("요청 안에서 앞선 링크와 정규화 URL이 같으면") {
                val links =
                    listOf(
                        ImportFixture.createSourceLink(url = "https://$host/same"),
                        ImportFixture.createSourceLink(url = "HTTPS://${host.uppercase()}:443/same/#top"),
                    )
                val preview = ImportFixture.createSource(links = links).preview(emptyList(), emptyList())

                Then("뒤 링크만 IN_FILE_DUPLICATE") {
                    preview.links.map { it.key } shouldContainExactly links.map { it.key }
                    preview.links.map { it.status } shouldContainExactly
                        listOf(ImportPreviewLinkStatus.NEW, ImportPreviewLinkStatus.IN_FILE_DUPLICATE)
                }
            }

            When("정규화할 수 없는 같은 URL이 내 링크와 요청에 있으면") {
                val broken = "https://exa mple ${RandomFixture.randomId()}"
                val links = List(2) { ImportFixture.createSourceLink(url = broken) }
                val preview = ImportFixture.createSource(links = links).preview(emptyList(), listOf(broken))

                Then("중복으로 보지 않고 모두 NEW") {
                    preview.links.map { it.status } shouldContainExactly
                        listOf(ImportPreviewLinkStatus.NEW, ImportPreviewLinkStatus.NEW)
                }
            }
        }

        Given("내 폴더 목록과 시작 폴더 항목") {
            val ownerId = RandomFixture.randomId()
            val now = Clock.System.now()

            fun planOf(
                folder: ImportSourceFolder,
                myFolders: List<com.qlink.folder.domain.Folder>,
            ): ImportPlan =
                ImportFixture
                    .createSource(folders = listOf(folder), links = listOf(ImportFixture.createSourceLink(folderKey = folder.key)))
                    .plan(ownerId, myFolders, emptyList(), now)

            When("folderId가 내 공유 아닌 폴더면") {
                val mine = FolderFixture.createFolderWith(ownerId = ownerId, name = uniqueName())
                val folder =
                    ImportFixture.createSourceFolder(
                        folderId = mine.id,
                        name = uniqueName(),
                        createdAt = RandomFixture.randomPastInstant(),
                    )
                val plan = planOf(folder, listOf(mine))

                Then("그 폴더를 쓰고 새 폴더를 만들지 않으며 항목 createdAt은 쓰지 않는다") {
                    val target = plan.folders.single()
                    target.kind shouldBe ImportFolderKind.EXISTING
                    target.folder shouldBe mine
                    target.folder.createdAt shouldBe mine.createdAt
                    plan.newFolders.shouldBeEmpty()
                    plan.links.single().folder shouldBe target
                }
            }

            When("folderId가 내 폴더 목록에 없으면(지워짐·남의 것)") {
                val folder = ImportFixture.createSourceFolder(folderId = RandomFixture.randomId(), name = uniqueName())
                val plan = planOf(folder, listOf(FolderFixture.createFolderWith(ownerId = ownerId, name = uniqueName())))

                Then("name으로 처리한다") {
                    val target = plan.folders.single()
                    target.kind shouldBe ImportFolderKind.NEW
                    target.folder.name shouldBe folder.name
                }
            }

            When("folderId가 지금은 공유된 내 폴더면") {
                val shared = FolderFixture.createSharedFolderOf(ownerId = ownerId, name = uniqueName())
                val reusable = FolderFixture.createFolderWith(ownerId = ownerId, name = uniqueName())
                val folder = ImportFixture.createSourceFolder(folderId = shared.id, name = reusable.name)
                val plan = planOf(folder, listOf(shared, reusable))

                Then("name으로 처리한다") {
                    val target = plan.folders.single()
                    target.kind shouldBe ImportFolderKind.EXISTING
                    target.folder shouldBe reusable
                }
            }

            When("name이 내 공유 아닌 폴더와 같으면") {
                val mine = FolderFixture.createFolderWith(ownerId = ownerId, name = uniqueName())
                val plan = planOf(ImportFixture.createSourceFolder(name = mine.name), listOf(mine))

                Then("그 폴더를 재사용하고 folderCount에 세지 않는다") {
                    plan.folders.single().folder shouldBe mine
                    plan.folderCount shouldBe 0
                }
            }

            When("name이 내 공유 폴더와 같으면") {
                val shared = FolderFixture.createSharedFolderOf(ownerId = ownerId, name = uniqueName())
                val plan = planOf(ImportFixture.createSourceFolder(name = shared.name), listOf(shared))

                Then("번호를 붙인 이름의 새 폴더를 만든다") {
                    val target = plan.folders.single()
                    target.kind shouldBe ImportFolderKind.NEW
                    target.folder.name shouldBe "${shared.name} 2"
                    target.folder.ownerId shouldBe ownerId
                    plan.folderCount shouldBe 1
                }
            }

            When("같은 이름이 없으면") {
                val folder = ImportFixture.createSourceFolder(name = uniqueName(), createdAt = RandomFixture.randomPastInstant())
                val plan = planOf(folder, listOf(FolderFixture.createFolderWith(ownerId = ownerId, name = uniqueName())))

                Then("그 이름의 새 폴더를 만든다") {
                    val target = plan.folders.single()
                    target.kind shouldBe ImportFolderKind.NEW
                    target.folder.name shouldBe folder.name
                    target.folder.createdAt shouldBe folder.createdAt
                    plan.newFolders shouldContainExactly listOf(target.folder)
                }
            }

            When("어떤 링크도 가리키지 않는 폴더 항목이면") {
                val plan =
                    ImportFixture
                        .createSource(
                            folders = listOf(ImportFixture.createSourceFolder()),
                            links = listOf(ImportFixture.createSourceLink()),
                        ).plan(ownerId, emptyList(), emptyList(), now)

                Then("폴더를 만들지 않는다") {
                    plan.folders.shouldBeEmpty()
                    plan.folderCount shouldBe 0
                }
            }

            When("두 항목이 같은 새 폴더 이름이 되면") {
                val name = uniqueName()
                val earlier = now - 10.days
                val folders =
                    listOf(
                        ImportFixture.createSourceFolder(name = name, createdAt = now - 1.days),
                        ImportFixture.createSourceFolder(name = " $name", createdAt = earlier),
                    )
                val plan =
                    ImportFixture
                        .createSource(folders = folders, links = folders.map { ImportFixture.createSourceLink(folderKey = it.key) })
                        .plan(ownerId, emptyList(), emptyList(), now)

                Then("새 폴더 하나로 합치고 createdAt은 더 이른 값이다") {
                    val target = plan.folders.single()
                    target.folder.name shouldBe name
                    target.folder.createdAt shouldBe earlier
                    plan.links.map { it.folder } shouldContainExactly listOf(target, target)
                }
            }

            When("새 폴더를 가리키는 링크가 모두 실패하거나 건너뛰어지면") {
                val savedUrl = ImportFixture.randomUniqueUrl()
                val folder = ImportFixture.createSourceFolder()
                val links =
                    listOf(
                        ImportFixture.createSourceLink(folderKey = folder.key, title = " "),
                        ImportFixture.createSourceLink(folderKey = folder.key, url = savedUrl),
                    )
                val plan =
                    ImportFixture
                        .createSource(
                            folders = listOf(folder),
                            links = links,
                        ).plan(ownerId, emptyList(), listOf(savedUrl), now)

                Then("그 새 폴더를 만들지 않는다") {
                    plan.folders.shouldBeEmpty()
                    plan.failedCount shouldBe 1
                    plan.skippedCount shouldBe 1
                }
            }

            When("새 폴더 항목의 createdAt이 null이면") {
                val plan = planOf(ImportFixture.createSourceFolder(createdAt = null), emptyList())

                Then("새 폴더 createdAt은 저장 시각이다") {
                    plan.folders
                        .single()
                        .folder.createdAt shouldBe now
                    plan.folders
                        .single()
                        .folder.updatedAt shouldBe now
                }
            }
        }

        Given("내 링크 URL 목록과 시작 링크 항목") {
            val ownerId = RandomFixture.randomId()
            val now = Clock.System.now()

            fun planOf(
                links: List<ImportSourceLink>,
                myUrls: List<String> = emptyList(),
                folders: List<ImportSourceFolder> = emptyList(),
            ): ImportPlan = ImportFixture.createSource(folders = folders, links = links).plan(ownerId, emptyList(), myUrls, now)

            When("링크가 유효하면") {
                val folder = ImportFixture.createSourceFolder()
                val filed = ImportFixture.createSourceLink(folderKey = folder.key)
                val unfiled = ImportFixture.createSourceLink()
                val plan = planOf(links = listOf(filed, unfiled), folders = listOf(folder))

                Then("가리킨 폴더 대상(null이면 미분류)으로 만들 링크에 담긴다") {
                    plan.links.map { it.url } shouldContainExactly listOf(filed.url, unfiled.url)
                    plan.links.map { it.title } shouldContainExactly listOf(filed.title, unfiled.title)
                    plan.links[0].folder shouldBe plan.folders.single()
                    plan.links[1].folder shouldBe null
                    plan.failures.shouldBeEmpty()
                }
            }

            When("createdAt이 과거면") {
                val createdAt = RandomFixture.randomPastInstant()
                val plan = planOf(links = listOf(ImportFixture.createSourceLink(createdAt = createdAt)))

                Then("그 값을 쓴다") {
                    plan.links.single().createdAt shouldBe createdAt
                }
            }

            When("createdAt이 null이면") {
                val plan = planOf(links = listOf(ImportFixture.createSourceLink(createdAt = null)))

                Then("저장 시각을 쓴다") {
                    plan.links.single().createdAt shouldBe now
                }
            }

            When("createdAt이 저장 시각보다 미래면") {
                val plan = planOf(links = listOf(ImportFixture.createSourceLink(createdAt = now + 1.days)))

                Then("저장 시각을 쓴다") {
                    plan.links.single().createdAt shouldBe now
                }
            }

            When("URL이 정확히 2,048자면") {
                val prefix = "https://${ImportFixture.randomKey()}.com/"
                val url = prefix + "a".repeat(ImportSource.LINK_URL_MAX_LENGTH - prefix.length)
                val plan = planOf(links = listOf(ImportFixture.createSourceLink(url = url)))

                Then("만들 링크에 담긴다") {
                    url shouldHaveLength ImportSource.LINK_URL_MAX_LENGTH
                    plan.links.single().url shouldBe url
                }
            }

            When("URL이 2,049자면") {
                val prefix = "https://${ImportFixture.randomKey()}.com/"
                val tooLong =
                    ImportFixture.createSourceLink(
                        url = prefix + "a".repeat(ImportSource.LINK_URL_MAX_LENGTH + 1 - prefix.length),
                    )
                val valid = ImportFixture.createSourceLink()
                val plan = planOf(links = listOf(tooLong, valid))

                Then("URL_INVALID 실패로 기록되고 나머지 링크는 그대로 담긴다") {
                    val failure = plan.failures.single()
                    failure.reason shouldBe ImportFailureReason.URL_INVALID
                    failure.url shouldBe tooLong.url
                    failure.title shouldBe tooLong.title
                    plan.links.map { it.url } shouldContainExactly listOf(valid.url)
                }
            }

            When("URL이 공백·잘못된 형식·호스트 없음·HTTP 아님이면") {
                val urls = listOf(" ", "https://exa mple.com", "https://.com", "ftp://example.com", "//${ImportFixture.randomKey()}.com/x")
                val plan = planOf(links = urls.map { ImportFixture.createSourceLink(url = it) })

                Then("URL_INVALID 실패") {
                    plan.failures.map { it.reason } shouldContainExactly List(urls.size) { ImportFailureReason.URL_INVALID }
                    plan.links.shouldBeEmpty()
                }
            }

            When("제목이 공백뿐이면") {
                val plan = planOf(links = listOf(ImportFixture.createSourceLink(title = "  ")))

                Then("TITLE_INVALID 실패") {
                    plan.failures.single().reason shouldBe ImportFailureReason.TITLE_INVALID
                }
            }

            When("제목이 정확히 300자면") {
                val title = RandomFixture.randomFixedSentence(ImportSource.LINK_TITLE_MAX_LENGTH)
                val plan = planOf(links = listOf(ImportFixture.createSourceLink(title = title)))

                Then("만들 링크에 담긴다") {
                    plan.links.single().title shouldBe title
                }
            }

            When("제목이 301자면") {
                val plan = planOf(links = listOf(ImportFixture.createSourceLink(title = RandomFixture.randomFixedSentence(301))))

                Then("TITLE_INVALID 실패") {
                    plan.failures.single().reason shouldBe ImportFailureReason.TITLE_INVALID
                }
            }

            When("URL이 내 링크와 정규화 URL이 같으면") {
                val saved = ImportFixture.randomUniqueUrl()
                val plan = planOf(links = listOf(ImportFixture.createSourceLink(url = "$saved/#fragment")), myUrls = listOf(saved))

                Then("건너뛰어 skippedCount에 센다(실패 아님)") {
                    plan.skippedCount shouldBe 1
                    plan.failures.shouldBeEmpty()
                    plan.links.shouldBeEmpty()
                }
            }

            When("요청 안에서 앞선 링크와 정규화 URL이 같으면") {
                val url = ImportFixture.randomUniqueUrl()
                val first = ImportFixture.createSourceLink(url = url)
                val plan = planOf(links = listOf(first, ImportFixture.createSourceLink(url = "$url?utm_source=x")))

                Then("뒤 링크를 건너뛴다") {
                    plan.links.map { it.url } shouldContainExactly listOf(first.url)
                    plan.skippedCount shouldBe 1
                }
            }

            When("앞선 같은 URL 링크가 실패했으면") {
                val url = ImportFixture.randomUniqueUrl()
                val valid = ImportFixture.createSourceLink(url = url)
                val plan = planOf(links = listOf(ImportFixture.createSourceLink(url = url, title = null), valid))

                Then("뒤 유효한 링크는 만들 링크에 담긴다") {
                    plan.failures.single().reason shouldBe ImportFailureReason.TITLE_INVALID
                    plan.links.single().url shouldBe valid.url
                    plan.skippedCount shouldBe 0
                }
            }

            When("모든 링크가 건너뛰어지면") {
                val urls = List(3) { ImportFixture.randomUniqueUrl() }
                val plan = planOf(links = urls.map { ImportFixture.createSourceLink(url = it) }, myUrls = urls)

                Then("만들 링크 0, 실패 0이다") {
                    plan.linkCount shouldBe 0
                    plan.failedCount shouldBe 0
                    plan.skippedCount shouldBe urls.size
                }
            }
        }
    })
