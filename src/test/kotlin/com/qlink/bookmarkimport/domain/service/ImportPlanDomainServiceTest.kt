package com.qlink.bookmarkimport.domain.service

import com.qlink.bookmarkimport.domain.ImportFailureReason
import com.qlink.bookmarkimport.domain.ImportFolderKind
import com.qlink.bookmarkimport.domain.ImportFolderMatchType
import com.qlink.bookmarkimport.domain.ImportPlan
import com.qlink.bookmarkimport.domain.ImportPreviewLinkStatus
import com.qlink.bookmarkimport.domain.ImportSource
import com.qlink.bookmarkimport.domain.ImportSourceFolder
import com.qlink.bookmarkimport.domain.ImportSourceLink
import com.qlink.bookmarkimport.domain.ThumbnailStatus
import com.qlink.bookmarkimport.dto.ImportPreview
import com.qlink.folder.domain.Folder
import com.qlink.link.domain.SourceType
import com.qlink.support.fixture.FolderFixture
import com.qlink.support.fixture.ImportFixture
import com.qlink.support.fixture.LinkFixture
import com.qlink.support.fixture.RandomFixture
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldHaveLength
import java.text.Normalizer
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

class ImportPlanDomainServiceTest :
    BehaviorSpec({
        fun uniqueName(): String = "폴더 ${RandomFixture.randomId()}"

        Given("내 폴더 목록과 미리보기 폴더 항목") {
            val ownerId = RandomFixture.randomId()

            fun previewOf(
                folders: List<ImportSourceFolder>,
                myFolders: List<Folder>,
            ): ImportPreview = ImportPlanDomainService.preview(ImportFixture.createSource(folders = folders), myFolders, emptyList())

            When("이름이 내 공유 아닌 폴더와 같으면") {
                val mine = FolderFixture.createFolderWith(ownerId = ownerId, name = uniqueName())
                val folder = ImportFixture.createSourceFolder(name = mine.name)
                val preview = previewOf(listOf(folder), listOf(mine))

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
                val preview = previewOf(listOf(ImportFixture.createSourceFolder(name = "  ${mine.name}\t")), listOf(mine))

                Then("EXISTING") {
                    preview.folders.single().matchType shouldBe ImportFolderMatchType.EXISTING
                }
            }

            When("NFC 정규화 전후로만 다르면(조합형·완성형 한글)") {
                val mine = FolderFixture.createFolderWith(ownerId = ownerId, name = uniqueName())
                val decomposed = Normalizer.normalize(mine.name, Normalizer.Form.NFD)
                val preview = previewOf(listOf(ImportFixture.createSourceFolder(name = decomposed)), listOf(mine))

                Then("EXISTING") {
                    decomposed shouldNotBe mine.name
                    preview.folders.single().matchType shouldBe ImportFolderMatchType.EXISTING
                }
            }

            When("대소문자만 다르면") {
                val mine = FolderFixture.createFolderWith(ownerId = ownerId, name = "folder-${RandomFixture.randomId()}")
                val preview = previewOf(listOf(ImportFixture.createSourceFolder(name = mine.name.uppercase())), listOf(mine))

                Then("NEW") {
                    preview.folders.single().matchType shouldBe ImportFolderMatchType.NEW
                }
            }

            When("이름이 내 공유 폴더와 같으면") {
                val mine = FolderFixture.createSharedFolderOf(ownerId = ownerId, name = uniqueName())
                val preview = previewOf(listOf(ImportFixture.createSourceFolder(name = mine.name)), listOf(mine))

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
                val preview = previewOf(listOf(ImportFixture.createSourceFolder(name = shared.name)), listOf(shared, numbered))

                Then("suggestedName은 이름 3") {
                    preview.folders.single().suggestedName shouldBe "${shared.name} 3"
                }
            }

            When("이름 2가 요청의 다른 폴더 이름으로 이미 있으면") {
                val shared = FolderFixture.createSharedFolderOf(ownerId = ownerId, name = uniqueName())
                val conflict = ImportFixture.createSourceFolder(name = shared.name)
                val other = ImportFixture.createSourceFolder(name = "${shared.name} 2")
                val preview = previewOf(listOf(conflict, other), listOf(shared))

                Then("suggestedName은 이름 3") {
                    preview.folders.single { it.key == conflict.key }.suggestedName shouldBe "${shared.name} 3"
                }
            }

            When("같은 이름의 SHARED_CONFLICT 항목이 둘이면") {
                val shared = FolderFixture.createSharedFolderOf(ownerId = ownerId, name = uniqueName())
                val preview = previewOf(List(2) { ImportFixture.createSourceFolder(name = shared.name) }, listOf(shared))

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
                val preview = previewOf(listOf(ImportFixture.createSourceFolder(name = name)), listOf(shared))

                Then("suggestedName은 원래 이름 끝을 줄여 번호를 붙인 100자다") {
                    val suggestedName = preview.folders.single().suggestedName
                    suggestedName shouldHaveLength ImportSource.FOLDER_NAME_MAX_LENGTH
                    suggestedName shouldBe "${name.take(ImportSource.FOLDER_NAME_MAX_LENGTH - 2)} 2"
                }
            }

            When("100자 이름의 SHARED_CONFLICT 번호 자리가 이모지 중간에 걸리면") {
                val name = RandomFixture.randomFixedSentence(ImportSource.FOLDER_NAME_MAX_LENGTH - 3) + "😀" + "b"
                val shared = FolderFixture.createSharedFolderOf(ownerId = ownerId, name = name)
                val preview = previewOf(listOf(ImportFixture.createSourceFolder(name = name)), listOf(shared))

                Then("이모지를 통째로 빼고 번호를 붙인다") {
                    name shouldHaveLength ImportSource.FOLDER_NAME_MAX_LENGTH
                    preview.folders.single().suggestedName shouldBe "${name.take(ImportSource.FOLDER_NAME_MAX_LENGTH - 3)} 2"
                }
            }

            When("일치하는 폴더가 없으면") {
                val name = uniqueName()
                val preview =
                    previewOf(
                        listOf(ImportFixture.createSourceFolder(name = " $name ")),
                        listOf(FolderFixture.createFolderWith(ownerId = ownerId, name = uniqueName())),
                    )

                Then("NEW, folderId는 null, suggestedName은 앞뒤 공백을 뗀 원래 이름") {
                    val actual = preview.folders.single()
                    actual.matchType shouldBe ImportFolderMatchType.NEW
                    actual.folderId shouldBe null
                    actual.suggestedName shouldBe name
                }
            }
        }

        Given("내 링크 URL 목록과 미리보기 링크 항목") {
            val saved = ImportFixture.randomUniqueUrl()
            val newUrl = ImportFixture.randomUniqueUrl()
            val links =
                listOf(
                    ImportFixture.createSourceLink(url = newUrl),
                    ImportFixture.createSourceLink(url = saved),
                    ImportFixture.createSourceLink(url = "$newUrl/"),
                )

            When("미리보기를 만들면") {
                val preview =
                    ImportPlanDomainService.preview(
                        source = ImportFixture.createSource(links = links),
                        myFolders = emptyList(),
                        myUrls = listOf(saved),
                    )

                Then("링크 항목이 요청 순서대로 key와 상태를 담는다") {
                    preview.links.map { it.key } shouldContainExactly links.map { it.key }
                    preview.links.map { it.status } shouldContainExactly
                        listOf(ImportPreviewLinkStatus.NEW, ImportPreviewLinkStatus.SAVED, ImportPreviewLinkStatus.IN_FILE_DUPLICATE)
                }
            }
        }

        Given("내 폴더 목록과 시작 폴더 항목") {
            val ownerId = RandomFixture.randomId()
            val now = Clock.System.now()

            fun planOf(
                folder: ImportSourceFolder,
                myFolders: List<Folder>,
            ): ImportPlan =
                ImportPlanDomainService.plan(
                    source =
                        ImportFixture.createSource(
                            folders = listOf(folder),
                            links = listOf(ImportFixture.createSourceLink(folderKey = folder.key)),
                        ),
                    ownerId = ownerId,
                    myFolders = myFolders,
                    myUrls = emptyList(),
                    now = now,
                )

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
                    target.folderId shouldBe mine.id
                    target.name shouldBe mine.name
                    target.createdAt shouldBe null
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
                    target.name shouldBe folder.name
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
                    target.folderId shouldBe reusable.id
                }
            }

            When("name이 내 공유 아닌 폴더와 같으면") {
                val mine = FolderFixture.createFolderWith(ownerId = ownerId, name = uniqueName())
                val plan = planOf(ImportFixture.createSourceFolder(name = mine.name), listOf(mine))

                Then("그 폴더를 재사용하고 folderCount에 세지 않는다") {
                    plan.folders.single().folderId shouldBe mine.id
                    plan.folderCount shouldBe 0
                }
            }

            When("name이 내 공유 폴더와 같으면") {
                val shared = FolderFixture.createSharedFolderOf(ownerId = ownerId, name = uniqueName())
                val plan = planOf(ImportFixture.createSourceFolder(name = shared.name), listOf(shared))

                Then("번호를 붙인 이름의 새 폴더를 만든다") {
                    val target = plan.folders.single()
                    target.kind shouldBe ImportFolderKind.NEW
                    target.name shouldBe "${shared.name} 2"
                    plan.folderCount shouldBe 1
                }
            }

            When("같은 이름이 없으면") {
                val folder = ImportFixture.createSourceFolder(name = uniqueName(), createdAt = RandomFixture.randomPastInstant())
                val plan = planOf(folder, listOf(FolderFixture.createFolderWith(ownerId = ownerId, name = uniqueName())))

                Then("그 이름의 새 폴더를 만든다") {
                    val target = plan.folders.single()
                    target.kind shouldBe ImportFolderKind.NEW
                    target.name shouldBe folder.name
                    target.createdAt shouldBe folder.createdAt
                    plan.newFolders shouldContainExactly listOf(target)
                }
            }

            When("어떤 링크도 가리키지 않는 폴더 항목이면") {
                val plan =
                    ImportPlanDomainService.plan(
                        source =
                            ImportFixture.createSource(
                                folders = listOf(ImportFixture.createSourceFolder()),
                                links = listOf(ImportFixture.createSourceLink()),
                            ),
                        ownerId = ownerId,
                        myFolders = emptyList(),
                        myUrls = emptyList(),
                        now = now,
                    )

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
                    ImportPlanDomainService.plan(
                        source =
                            ImportFixture.createSource(
                                folders = folders,
                                links = folders.map { ImportFixture.createSourceLink(folderKey = it.key) },
                            ),
                        ownerId = ownerId,
                        myFolders = emptyList(),
                        myUrls = emptyList(),
                        now = now,
                    )

                Then("새 폴더 하나로 합치고 createdAt은 더 이른 값이다") {
                    val target = plan.folders.single()
                    target.name shouldBe name
                    target.createdAt shouldBe earlier
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
                    ImportPlanDomainService.plan(
                        source = ImportFixture.createSource(folders = listOf(folder), links = links),
                        ownerId = ownerId,
                        myFolders = emptyList(),
                        myUrls = listOf(savedUrl),
                        now = now,
                    )

                Then("그 새 폴더를 만들지 않는다") {
                    plan.folders.shouldBeEmpty()
                    plan.failedCount shouldBe 1
                    plan.skippedCount shouldBe 1
                }
            }

            When("새 폴더 항목의 createdAt이 null이면") {
                val plan = planOf(ImportFixture.createSourceFolder(createdAt = null), emptyList())

                Then("새 폴더 createdAt은 저장 시각이다") {
                    plan.folders.single().createdAt shouldBe now
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
            ): ImportPlan =
                ImportPlanDomainService.plan(
                    source = ImportFixture.createSource(folders = folders, links = links),
                    ownerId = ownerId,
                    myFolders = emptyList(),
                    myUrls = myUrls,
                    now = now,
                )

            When("링크가 유효하면") {
                val folder = ImportFixture.createSourceFolder()
                val filed = ImportFixture.createSourceLink(folderKey = folder.key, createdAt = RandomFixture.randomPastInstant())
                val unfiled = ImportFixture.createSourceLink()
                val plan = planOf(links = listOf(filed, unfiled), folders = listOf(folder))

                Then("가리킨 폴더 대상(null이면 미분류)으로 만들 링크에 담긴다") {
                    plan.links.map { it.url } shouldContainExactly listOf(filed.url, unfiled.url)
                    plan.links.map { it.title } shouldContainExactly listOf(filed.title, unfiled.title)
                    plan.links.map { it.createdAt } shouldContainExactly listOf(filed.createdAt, now)
                    plan.links[0].folder shouldBe plan.folders.single()
                    plan.links[1].folder shouldBe null
                    plan.failures.shouldBeEmpty()
                }
            }

            When("한 링크가 실패하면") {
                val prefix = "https://${ImportFixture.randomKey()}.com/"
                val tooLong =
                    ImportFixture.createSourceLink(
                        url = prefix + "a".repeat(ImportSource.LINK_URL_MAX_LENGTH + 1 - prefix.length),
                    )
                val valid = ImportFixture.createSourceLink()
                val plan = planOf(links = listOf(tooLong, valid))

                Then("그 링크만 실패로 기록되고 나머지 링크는 그대로 담긴다") {
                    val failure = plan.failures.single()
                    failure.reason shouldBe ImportFailureReason.URL_INVALID
                    failure.url shouldBe tooLong.url
                    failure.title shouldBe tooLong.title
                    plan.links.map { it.url } shouldContainExactly listOf(valid.url)
                }
            }

            When("URL이 정확히 2,048자면") {
                val prefix = "https://${ImportFixture.randomKey()}.com/"
                val url = prefix + "a".repeat(ImportSource.LINK_URL_MAX_LENGTH - prefix.length)
                val plan = planOf(links = listOf(ImportFixture.createSourceLink(url = url)))

                Then("만들 링크에 담긴다") {
                    url shouldHaveLength ImportSource.LINK_URL_MAX_LENGTH
                    plan.links.single().url shouldBe url
                    plan.failures.shouldBeEmpty()
                }
            }

            When("제목이 정확히 300자면") {
                val title = RandomFixture.randomFixedSentence(ImportSource.LINK_TITLE_MAX_LENGTH)
                val plan = planOf(links = listOf(ImportFixture.createSourceLink(title = title)))

                Then("만들 링크에 담긴다") {
                    plan.links.single().title shouldBe title
                    plan.failures.shouldBeEmpty()
                }
            }

            When("URL이 공백·잘못된 형식·호스트 없음·스킴 없음·HTTP 아님이면") {
                val urls = listOf(" ", "https://exa mple.com", "https://.com", "//${ImportFixture.randomKey()}.com/x", "ftp://example.com")
                val plan = planOf(links = urls.map { ImportFixture.createSourceLink(url = it) })

                Then("URL_INVALID 실패") {
                    plan.failures.map { it.reason } shouldContainExactly List(urls.size) { ImportFailureReason.URL_INVALID }
                    plan.links.shouldBeEmpty()
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

        Given("기존 폴더·새 폴더·미분류 링크가 있는 계획") {
            val ownerId = RandomFixture.randomId()
            val importId = RandomFixture.randomId()
            val now = Clock.System.now()
            val existing = FolderFixture.createFolderWith(ownerId = ownerId, name = "기존 ${RandomFixture.randomId()}")
            val existingSource = ImportFixture.createSourceFolder(folderId = existing.id)
            val newSource = ImportFixture.createSourceFolder(createdAt = RandomFixture.randomPastInstant())
            val links =
                listOf(
                    ImportFixture.createSourceLink(folderKey = existingSource.key),
                    ImportFixture.createSourceLink(folderKey = newSource.key, createdAt = RandomFixture.randomPastInstant()),
                    ImportFixture.createSourceLink(),
                )
            val plan =
                ImportPlanDomainService.plan(
                    source = ImportFixture.createSource(folders = listOf(existingSource, newSource), links = links),
                    ownerId = ownerId,
                    myFolders = listOf(existing),
                    myUrls = emptyList(),
                    now = now,
                )
            val savedNewFolder = FolderFixture.createFolderWith(ownerId = ownerId, name = plan.newFolders.single().name)

            When("새 폴더를 만들면") {
                val folders = ImportPlanDomainService.newFolders(plan)

                Then("소유자·이름·createdAt이 계획대로인 가져온 폴더다") {
                    val folder = folders.single()
                    folder.id shouldBe null
                    folder.ownerId shouldBe ownerId
                    folder.name shouldBe newSource.name
                    folder.createdAt shouldBe newSource.createdAt
                    folder.updatedAt shouldBe newSource.createdAt
                }
            }

            When("저장된 새 폴더로 링크를 만들면") {
                val created = ImportPlanDomainService.links(plan, listOf(savedNewFolder))

                Then("기존 폴더·저장된 새 폴더·미분류에 sourceType=IMPORT 링크로 만들어진다") {
                    created.map { it.folderId } shouldContainExactly listOf(existing.id, savedNewFolder.id, null)
                    created.map { it.url } shouldContainExactly links.map { it.url }
                    created.map { it.createdAt } shouldContainExactly plan.links.map { it.createdAt }
                    created.forEach { it.sourceType shouldBe SourceType.IMPORT }
                    created.forEach { it.ownerId shouldBe ownerId }
                }
            }

            When("저장된 새 폴더로 행을 만들면") {
                val rows = ImportPlanDomainService.importFolders(plan, importId, listOf(savedNewFolder))

                Then("대상 폴더마다 종류와 저장된 폴더 id가 담긴다") {
                    rows.map { it.folderId } shouldContainExactly listOf(existing.id, savedNewFolder.id)
                    rows.map { it.kind } shouldContainExactly listOf(ImportFolderKind.EXISTING, ImportFolderKind.NEW)
                    rows.map { it.name } shouldContainExactly listOf(existing.name, savedNewFolder.name)
                    rows.forEach { it.importId shouldBe importId }
                }
            }

            When("저장된 링크로 행을 만들면") {
                val savedLinks =
                    ImportPlanDomainService.links(plan, listOf(savedNewFolder)).map {
                        LinkFixture.createRandomLinkOf(ownerId = ownerId, folderId = it.folderId, url = it.url, title = it.title)
                    }
                val savedRows =
                    ImportPlanDomainService.importFolders(plan, importId, listOf(savedNewFolder)).map {
                        ImportFixture.createImportFolderOf(importId = importId, folderId = it.folderId, kind = it.kind, name = it.name)
                    }
                val rows = ImportPlanDomainService.importLinks(importId, savedLinks, savedRows)

                Then("링크 id와 들어간 가져오기 폴더 행 id가 담기고 썸네일 작업은 PENDING이다") {
                    rows.map { it.linkId } shouldContainExactly savedLinks.map { it.id }
                    rows.map { it.importFolderId } shouldContainExactly listOf(savedRows[0].id, savedRows[1].id, null)
                    rows.map { it.title } shouldContainExactly savedLinks.map { it.title }
                    rows.forEach { it.thumbnailStatus shouldBe ThumbnailStatus.PENDING }
                    rows.forEach { it.importId shouldBe importId }
                }
            }
        }
    })
