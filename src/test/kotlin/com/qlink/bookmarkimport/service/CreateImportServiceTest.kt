@file:OptIn(ExperimentalUuidApi::class)

package com.qlink.bookmarkimport.service

import com.qlink.bookmarkimport.domain.BookmarkImport
import com.qlink.bookmarkimport.domain.ImportFailureReason
import com.qlink.bookmarkimport.domain.ImportFolderKind
import com.qlink.bookmarkimport.domain.ImportStatus
import com.qlink.bookmarkimport.domain.ThumbnailStatus
import com.qlink.bookmarkimport.dto.CreateImportFolderRequest
import com.qlink.bookmarkimport.dto.CreateImportLinkRequest
import com.qlink.bookmarkimport.dto.CreateImportRequest
import com.qlink.bookmarkimport.repository.BookmarkImportRepository
import com.qlink.bookmarkimport.worker.ImportThumbnailSignal
import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import com.qlink.common.transaction.TransactionRunner
import com.qlink.folder.domain.Folder
import com.qlink.folder.repository.FolderRepository
import com.qlink.link.domain.Link
import com.qlink.link.domain.SourceType
import com.qlink.link.repository.LinkRepository
import com.qlink.link.repository.table.Links
import com.qlink.linkcontent.domain.ContentPriority
import com.qlink.linkcontent.domain.ContentStatus
import com.qlink.linkcontent.repository.LinkContentRepository
import com.qlink.linkcontent.repository.table.LinkContents
import com.qlink.support.BaseServiceTest
import com.qlink.support.fixture.FolderFixture
import com.qlink.support.fixture.ImportFixture
import com.qlink.support.fixture.ImportRequestFixture
import com.qlink.support.fixture.LinkFixture
import com.qlink.support.fixture.RandomFixture
import com.qlink.support.fixture.UserFixture
import com.qlink.support.koinGet
import com.qlink.user.domain.User
import com.qlink.user.repository.UserRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.throwables.shouldThrowWithMessage
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import java.sql.Connection
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class CreateImportServiceTest :
    BaseServiceTest({
        val createImportService = koinGet<CreateImportService>()
        val userRepository = koinGet<UserRepository>()
        val folderRepository = koinGet<FolderRepository>()
        val linkRepository = koinGet<LinkRepository>()
        val bookmarkImportRepository = koinGet<BookmarkImportRepository>()
        val importThumbnailSignal = koinGet<ImportThumbnailSignal>()
        val linkContentRepository = koinGet<LinkContentRepository>()
        val tx = koinGet<TransactionRunner>()
        val db = koinGet<Database>()

        suspend fun <T> withCommittedUser(block: suspend (User) -> T): T =
            CoroutineScope(Dispatchers.IO)
                .async {
                    val owner = tx.required { userRepository.insert(UserFixture.createRandomValidUser()) }
                    try {
                        block(owner)
                    } finally {
                        tx.required { userRepository.deleteById(owner.id!!) }
                    }
                }.await()

        fun countSessionsWaitingOnLock(): Int =
            TransactionManager.current().exec(
                "SELECT count(*) FROM pg_stat_activity WHERE wait_event_type = 'Lock' AND datname = current_database()",
            ) { it.takeIf { row -> row.next() }?.getInt(1) } ?: 0

        Given("폴더·링크가 있는 로그인 사용자") {
            lateinit var user: User
            lateinit var otherUser: User
            lateinit var existingFolder: Folder
            lateinit var savedLink: Link
            var savedLinkSeed = 0L

            beforeTest {
                savedLinkSeed = RandomFixture.randomId()
                user = userRepository.insert(UserFixture.createRandomValidUser())
                otherUser = userRepository.insert(UserFixture.createRandomValidUser())
                existingFolder =
                    folderRepository.insert(
                        FolderFixture.createFolderWith(
                            ownerId = user.id!!,
                            name = ImportRequestFixture.randomFolderName(),
                            createdAt = RandomFixture.randomPastInstant(),
                        ),
                    )
                savedLink =
                    linkRepository.insert(
                        LinkFixture.createRandomLinkOf(
                            ownerId = user.id!!,
                            url = "https://example-$savedLinkSeed.com/path-$savedLinkSeed?b=1&utm_source=mail",
                        ),
                    )
            }

            When("새 폴더·기존 폴더·미분류 링크를 가져오면") {
                val newFolder = ImportRequestFixture.createFolderRequest()
                lateinit var existingFolderRequest: CreateImportFolderRequest
                lateinit var request: CreateImportRequest

                beforeTest {
                    existingFolderRequest =
                        ImportRequestFixture.createFolderRequest(folderId = existingFolder.id, name = existingFolder.name)
                    request =
                        ImportRequestFixture.createImportRequest(
                            folders = listOf(newFolder, existingFolderRequest),
                            links =
                                ImportRequestFixture.createLinkRequestsIn(folder = newFolder, count = 2) +
                                    ImportRequestFixture.createLinkRequestsIn(folder = existingFolderRequest, count = 1) +
                                    ImportRequestFixture.createLinkRequestsIn(folder = null, count = 1),
                        )
                }

                val create =
                    suspend {
                        createImportService.createImport(user.id!!, request)
                    }

                Then("기록에 상태·개수가 저장되고, 새 폴더·링크·가져오기 폴더 행·가져오기 링크 행이 함께 저장된다") {
                    create()

                    val saved =
                        bookmarkImportRepository
                            .findByOwnerIdAndClientRequestId(
                                user.id!!,
                                request.clientRequestId,
                            ).shouldNotBeNull()
                    saved.status shouldBe ImportStatus.COMPLETED
                    saved.linkCount shouldBe 4
                    saved.folderCount shouldBe 1
                    saved.skippedCount shouldBe 0
                    saved.failedCount shouldBe 0

                    val createdFolder = folderRepository.findAllByOwnerId(user.id!!).single { it.name == newFolder.name }
                    val importFolders = bookmarkImportRepository.findFoldersByImportId(saved.id!!)
                    importFolders.map { it.folderId to it.kind } shouldContainExactlyInAnyOrder
                        listOf(createdFolder.id!! to ImportFolderKind.NEW, existingFolder.id!! to ImportFolderKind.EXISTING)

                    val importLinks = bookmarkImportRepository.findLinksByImportId(saved.id)
                    importLinks.map { it.thumbnailStatus }.toSet() shouldBe setOf(ThumbnailStatus.PENDING)
                    val importFolderIdByFolderId = importFolders.associate { it.folderId to it.id }
                    val folderIdByKey = mapOf(newFolder.key to createdFolder.id, existingFolderRequest.key to existingFolder.id)
                    val links = linkRepository.findAllByIds(importLinks.map { it.linkId })
                    links.map { it.url to it.folderId } shouldContainExactlyInAnyOrder
                        request.links.map { it.url to it.folderKey?.let(folderIdByKey::getValue) }
                    links.map { it.sourceType }.toSet() shouldBe setOf(SourceType.IMPORT)
                    importLinks.map { it.url to it.importFolderId } shouldContainExactlyInAnyOrder
                        request.links.map { it.url to it.folderKey?.let(folderIdByKey::getValue)?.let(importFolderIdByFolderId::getValue) }
                }
            }

            When("링크·새 폴더에 createdAt을 주면") {
                val linkCreatedAt = RandomFixture.randomPastInstant()
                val folderCreatedAt = RandomFixture.randomPastInstant()
                val folder = ImportRequestFixture.createFolderRequest(createdAt = folderCreatedAt)
                val link = ImportRequestFixture.createLinkRequest(folderKey = folder.key, createdAt = linkCreatedAt)

                val create =
                    suspend {
                        createImportService.createImport(
                            user.id!!,
                            ImportRequestFixture.createImportRequest(folders = listOf(folder), links = listOf(link)),
                        )
                    }

                Then("저장된 created_at·updated_at이 그 값이다") {
                    val importId = create().id

                    val savedFolder = folderRepository.findAllByOwnerId(user.id!!).single { it.name == folder.name }
                    savedFolder.createdAt shouldBe folderCreatedAt
                    savedFolder.updatedAt shouldBe folderCreatedAt
                    val savedLinkRow =
                        linkRepository
                            .findById(
                                bookmarkImportRepository.findLinksByImportId(importId).single().linkId,
                            ).shouldNotBeNull()
                    savedLinkRow.createdAt shouldBe linkCreatedAt
                    savedLinkRow.updatedAt shouldBe linkCreatedAt
                }
            }

            When("folderId로 기존 폴더를 가리키면") {
                lateinit var folder: CreateImportFolderRequest
                lateinit var link: CreateImportLinkRequest

                beforeTest {
                    folder =
                        ImportRequestFixture.createFolderRequest(
                            folderId = existingFolder.id,
                            createdAt = RandomFixture.randomPastInstant(),
                        )
                    link = ImportRequestFixture.createLinkRequest(folderKey = folder.key)
                }

                val create =
                    suspend {
                        createImportService.createImport(
                            user.id!!,
                            ImportRequestFixture.createImportRequest(folders = listOf(folder), links = listOf(link)),
                        )
                    }

                Then("링크가 그 폴더에 저장되고 그 폴더의 created_at은 그대로다") {
                    create()

                    linkRepository.findAllByFolderIds(listOf(existingFolder.id!!)).map { it.url } shouldContainExactly listOf(link.url)
                    folderRepository.findById(existingFolder.id!!)?.createdAt shouldBe existingFolder.createdAt
                    folderRepository.findAllByOwnerId(user.id!!).map { it.id } shouldContainExactly listOf(existingFolder.id)
                }
            }

            When("folderId가 다른 사용자의 폴더면") {
                lateinit var otherFolder: Folder
                lateinit var folder: CreateImportFolderRequest
                lateinit var link: CreateImportLinkRequest

                beforeTest {
                    otherFolder = folderRepository.insert(FolderFixture.createValidUnsharedFolder(otherUser.id!!))
                    folder = ImportRequestFixture.createFolderRequest(folderId = otherFolder.id)
                    link = ImportRequestFixture.createLinkRequest(folderKey = folder.key)
                }

                val create =
                    suspend {
                        createImportService.createImport(
                            user.id!!,
                            ImportRequestFixture.createImportRequest(folders = listOf(folder), links = listOf(link)),
                        )
                    }

                Then("그 폴더는 그대로이고 name으로 내 폴더가 정해진다") {
                    create()

                    linkRepository.findAllByFolderIds(listOf(otherFolder.id!!)).shouldBeEmpty()
                    folderRepository.findById(otherFolder.id!!)?.name shouldBe otherFolder.name
                    val myFolder = folderRepository.findAllByOwnerId(user.id!!).single { it.name == folder.name }
                    linkRepository.findAllByFolderIds(listOf(myFolder.id!!)).map { it.url } shouldContainExactly listOf(link.url)
                }
            }

            When("이미 저장된 내 링크와 정규화 URL이 같은 링크가 있으면") {
                val newLink = ImportRequestFixture.createLinkRequest()
                lateinit var request: CreateImportRequest

                beforeTest {
                    request =
                        ImportRequestFixture.createImportRequest(
                            links =
                                listOf(
                                    ImportRequestFixture.createLinkRequest(
                                        url = "HTTPS://EXAMPLE-$savedLinkSeed.COM/path-$savedLinkSeed/?b=1#top",
                                    ),
                                    newLink,
                                ),
                        )
                }

                val create =
                    suspend {
                        createImportService.createImport(user.id!!, request)
                    }

                Then("그 링크는 저장되지 않고 skipped_count에 센다") {
                    create()

                    val saved =
                        bookmarkImportRepository
                            .findByOwnerIdAndClientRequestId(
                                user.id!!,
                                request.clientRequestId,
                            ).shouldNotBeNull()
                    saved.skippedCount shouldBe 1
                    saved.linkCount shouldBe 1
                    bookmarkImportRepository.findLinksByImportId(saved.id!!).map { it.url } shouldContainExactly listOf(newLink.url)
                    linkRepository.findAllUrlsByOwnerId(user.id!!) shouldContainExactlyInAnyOrder listOf(savedLink.url, newLink.url)
                }
            }

            When("새 링크와 이미 저장된 내 링크가 섞인 가져오기에서 새 링크가 저장되면") {
                lateinit var request: CreateImportRequest

                beforeTest {
                    request =
                        ImportRequestFixture.createImportRequest(
                            links =
                                ImportRequestFixture.createLinkRequestsIn(folder = null, count = 2) +
                                    ImportRequestFixture.createLinkRequest(url = savedLink.url),
                        )
                }

                val create =
                    suspend {
                        createImportService.createImport(user.id!!, request)
                    }

                Then("그 링크들에만 BULK 행이 있고 중복 skip 링크에는 없다") {
                    val summary = create()
                    val importedLinks = bookmarkImportRepository.findLinksByImportId(summary.id)
                    val contents = importedLinks.map { linkContentRepository.findByLinkId(it.linkId) }

                    importedLinks shouldHaveSize 2
                    contents.forEachIndexed { index, content ->
                        content.shouldNotBeNull()
                        content.status shouldBe ContentStatus.PENDING
                        content.priority shouldBe ContentPriority.BULK
                        content.sourceUrl shouldBe importedLinks[index].url
                    }
                    linkContentRepository.findByLinkId(savedLink.id!!) shouldBe null
                }
            }

            When("새 링크를 저장한 가져오기를 같은 clientRequestId로 다시 보내면") {
                val clientRequestId = Uuid.random()
                val first = ImportRequestFixture.createImportRequest(clientRequestId = clientRequestId)
                val second =
                    ImportRequestFixture.createImportRequest(
                        clientRequestId = clientRequestId,
                        links = ImportRequestFixture.createLinkRequestsIn(folder = null, count = 2),
                    )

                Then("본문 행이 늘지 않는다") {
                    createImportService.createImport(user.id!!, first)
                    val before = countLinkContentsOf(user.id!!)

                    createImportService.createImport(user.id!!, second)

                    before shouldBe 1L
                    countLinkContentsOf(user.id!!) shouldBe before
                    second.links.forEach { requested ->
                        linkRepository.findAllUrlsByOwnerId(user.id!!).contains(requested.url) shouldBe false
                    }
                }
            }

            When("모든 링크가 이미 저장된 내 링크와 같은 가져오기를 같은 clientRequestId로 두 번 보내면") {
                lateinit var request: CreateImportRequest

                beforeTest {
                    val folder = ImportRequestFixture.createFolderRequest()
                    request =
                        ImportRequestFixture.createImportRequest(
                            folders = listOf(folder),
                            links = listOf(ImportRequestFixture.createLinkRequest(url = savedLink.url, folderKey = folder.key)),
                        )
                }

                val create =
                    suspend {
                        createImportService.createImport(user.id!!, request)
                    }

                Then("두 번 모두 예외가 발생하고 기록·폴더·링크가 늘지 않는다") {
                    repeat(2) {
                        shouldThrowWithMessage<BusinessException>(ErrorCode.IMPORT_NOTHING_TO_IMPORT.message) {
                            create()
                        }
                    }

                    bookmarkImportRepository.findByOwnerIdAndClientRequestId(user.id!!, request.clientRequestId) shouldBe null
                    bookmarkImportRepository.search(ownerId = user.id!!, cursor = null, size = 10).shouldBeEmpty()
                    folderRepository.findAllByOwnerId(user.id!!).map { it.id } shouldContainExactly listOf(existingFolder.id)
                    linkRepository.findAllUrlsByOwnerId(user.id!!) shouldContainExactly listOf(savedLink.url)
                }
            }

            When("일부 링크가 검증에 실패하면") {
                val validLink = ImportRequestFixture.createLinkRequest()
                val blankTitleLink = ImportRequestFixture.createLinkRequest(title = " ")
                val notHttpLink = ImportRequestFixture.createLinkRequest(url = "ftp://example-${RandomFixture.randomId()}.com/file")
                val tooLongUrlLink =
                    ImportRequestFixture.createLinkRequest(url = "${ImportFixture.randomUniqueUrl()}/${"a".repeat(2048)}")
                val request =
                    ImportRequestFixture.createImportRequest(links = listOf(validLink, blankTitleLink, notHttpLink, tooLongUrlLink))

                val create =
                    suspend {
                        createImportService.createImport(user.id!!, request)
                    }

                Then("나머지가 저장되고 실패 행에 제목·URL·사유가 저장된다") {
                    create()

                    val saved =
                        bookmarkImportRepository
                            .findByOwnerIdAndClientRequestId(
                                user.id!!,
                                request.clientRequestId,
                            ).shouldNotBeNull()
                    saved.status shouldBe ImportStatus.PARTIALLY_FAILED
                    saved.linkCount shouldBe 1
                    saved.failedCount shouldBe 3
                    bookmarkImportRepository.findLinksByImportId(saved.id!!).map { it.url } shouldContainExactly listOf(validLink.url)
                    bookmarkImportRepository
                        .findFailuresByImportId(
                            saved.id,
                        ).map { Triple(it.title, it.url, it.reason) } shouldContainExactlyInAnyOrder
                        listOf(
                            Triple(blankTitleLink.title, blankTitleLink.url, ImportFailureReason.TITLE_INVALID),
                            Triple(notHttpLink.title, notHttpLink.url, ImportFailureReason.URL_INVALID),
                            Triple(tooLongUrlLink.title, tooLongUrlLink.url, ImportFailureReason.URL_INVALID),
                        )
                }
            }

            When("링크 3,000개를 가져오면") {
                val request =
                    ImportRequestFixture.createImportRequest(
                        links = ImportRequestFixture.createLinkRequestsIn(folder = null, count = 3000),
                    )

                val create =
                    suspend {
                        createImportService.createImport(user.id!!, request)
                    }

                Then("3,000개 링크와 항목 행이 모두 저장된다") {
                    val importId = create().id

                    bookmarkImportRepository.findById(importId)?.linkCount shouldBe 3000
                    bookmarkImportRepository.findLinksByImportId(importId) shouldHaveSize 3000
                    linkRepository.findAllUrlsByOwnerId(user.id!!) shouldHaveSize 3001
                }
            }

            When("저장이 끝나면") {
                beforeTest {
                    withTimeoutOrNull(100.milliseconds) { importThumbnailSignal.await() }
                }

                val create =
                    suspend {
                        createImportService.createImport(user.id!!, ImportRequestFixture.createImportRequest())
                    }

                Then("썸네일 신호가 온다") {
                    create()

                    withTimeoutOrNull(1.seconds) { importThumbnailSignal.await() } shouldBe Unit
                }
            }

            When("가져오기 트랜잭션 안에서 격리 수준을 보면") {
                val isolationAfterImport =
                    suspend {
                        withCommittedUser { owner ->
                            suspendTransaction(db = db, transactionIsolation = Connection.TRANSACTION_REPEATABLE_READ) {
                                try {
                                    createImportService.createImport(owner.id!!, ImportRequestFixture.createImportRequest())
                                    exec("SHOW transaction_isolation") { it.takeIf { row -> row.next() }?.getString(1) }
                                } finally {
                                    rollback()
                                }
                            }
                        }
                    }

                Then("SHOW transaction_isolation이 read committed다") {
                    isolationAfterImport() shouldBe "read committed"
                }
            }

            When("같은 clientRequestId로 다시 보내면") {
                val clientRequestId = Uuid.random()
                val first = ImportRequestFixture.createImportRequest(clientRequestId = clientRequestId)
                val second =
                    ImportRequestFixture.createImportRequest(
                        clientRequestId = clientRequestId,
                        folders = listOf(ImportRequestFixture.createFolderRequest()),
                        links = ImportRequestFixture.createLinkRequestsIn(folder = null, count = 2),
                    )

                val createTwice =
                    suspend {
                        createImportService.createImport(user.id!!, first) to createImportService.createImport(user.id!!, second)
                    }

                Then("기록·폴더·링크가 늘지 않고 처음 기록의 요약을 돌려준다") {
                    val (firstSummary, secondSummary) = createTwice()

                    secondSummary shouldBe firstSummary
                    bookmarkImportRepository.search(ownerId = user.id!!, cursor = null, size = 10).map { it.id } shouldContainExactly
                        listOf(firstSummary.id)
                    folderRepository.findAllByOwnerId(user.id!!).map { it.id } shouldContainExactly listOf(existingFolder.id)
                    linkRepository.findAllUrlsByOwnerId(user.id!!) shouldContainExactlyInAnyOrder
                        listOf(savedLink.url, first.links.single().url)
                }
            }

            When("다른 사용자가 같은 clientRequestId를 쓰면") {
                val clientRequestId = Uuid.random()

                val createForBoth =
                    suspend {
                        createImportService.createImport(
                            user.id!!,
                            ImportRequestFixture.createImportRequest(clientRequestId = clientRequestId),
                        )
                        createImportService.createImport(
                            otherUser.id!!,
                            ImportRequestFixture.createImportRequest(clientRequestId = clientRequestId),
                        )
                    }

                Then("각자의 기록이 따로 저장된다") {
                    createForBoth()

                    val mine = bookmarkImportRepository.findByOwnerIdAndClientRequestId(user.id!!, clientRequestId).shouldNotBeNull()
                    val others = bookmarkImportRepository.findByOwnerIdAndClientRequestId(otherUser.id!!, clientRequestId).shouldNotBeNull()
                    mine.id shouldNotBe others.id
                }
            }

            When("검증에 걸려 거절된 clientRequestId로 올바른 요청을 다시 보내면") {
                val clientRequestId = Uuid.random()
                val valid = ImportRequestFixture.createImportRequest(clientRequestId = clientRequestId)

                beforeTest {
                    shouldThrow<BusinessException> {
                        createImportService.createImport(
                            user.id!!,
                            ImportRequestFixture.createImportRequest(clientRequestId = clientRequestId, fileName = " "),
                        )
                    }
                }

                val retry =
                    suspend {
                        createImportService.createImport(user.id!!, valid)
                    }

                Then("새 기록이 저장된다") {
                    retry()

                    bookmarkImportRepository.findByOwnerIdAndClientRequestId(user.id!!, clientRequestId)?.fileName shouldBe valid.fileName
                }
            }

            When("같은 이름의 새 폴더와 같은 URL을 가진 두 가져오기를 동시에 보내면") {
                val folderName = ImportRequestFixture.randomFolderName()
                val url = ImportFixture.randomUniqueUrl()
                val requests =
                    List(2) {
                        val folder = ImportRequestFixture.createFolderRequest(name = folderName)

                        ImportRequestFixture.createImportRequest(
                            folders = listOf(folder),
                            links = listOf(ImportRequestFixture.createLinkRequest(url = url, folderKey = folder.key)),
                        )
                    }

                val importConcurrently =
                    suspend {
                        withCommittedUser { owner ->
                            val results =
                                coroutineScope {
                                    requests.map { async { runCatching { createImportService.createImport(owner.id!!, it) } } }.awaitAll()
                                }
                            results to
                                tx.readOnly {
                                    Triple(
                                        bookmarkImportRepository.search(ownerId = owner.id!!, cursor = null, size = 10).size,
                                        folderRepository.findAllByOwnerId(owner.id).count { it.name == folderName },
                                        linkRepository.findAllUrlsByOwnerId(owner.id).count { it == url },
                                    )
                                }
                        }
                    }

                Then("하나만 저장되고 다른 하나는 새로 가져올 링크가 없어 거절되며, 기록·그 이름의 폴더·그 URL의 링크는 하나씩이다") {
                    val (results, counts) = importConcurrently()

                    results.count { it.isSuccess } shouldBe 1
                    results.mapNotNull { it.exceptionOrNull()?.message } shouldContainExactly
                        listOf(ErrorCode.IMPORT_NOTHING_TO_IMPORT.message)
                    counts shouldBe Triple(1, 1, 1)
                }
            }

            When("저장하는 사이 같은 clientRequestId 기록이 먼저 커밋되면") {
                val request = ImportRequestFixture.createImportRequest()

                val createDuringCompetingSave =
                    suspend {
                        withCommittedUser { owner ->
                            coroutineScope {
                                val inserted = CompletableDeferred<BookmarkImport>()
                                val release = CompletableDeferred<Unit>()
                                val competitor =
                                    async {
                                        tx.required {
                                            bookmarkImportRepository
                                                .insert(
                                                    ImportFixture.createBookmarkImportOf(
                                                        ownerId = owner.id!!,
                                                        clientRequestId = request.clientRequestId,
                                                    ),
                                                ).also {
                                                    inserted.complete(it)
                                                    release.await()
                                                }
                                        }
                                    }
                                val competing = inserted.await()
                                val summary = async { createImportService.createImport(owner.id!!, request) }
                                withTimeout(10.seconds) {
                                    while (tx.readOnly { countSessionsWaitingOnLock() } == 0) {
                                        delay(20.milliseconds)
                                    }
                                }
                                release.complete(Unit)
                                competitor.await()

                                Triple(
                                    competing.id,
                                    summary.await().id,
                                    tx.readOnly {
                                        bookmarkImportRepository.search(ownerId = owner.id!!, cursor = null, size = 10).size to
                                            linkRepository.findAllUrlsByOwnerId(owner.id).size
                                    },
                                )
                            }
                        }
                    }

                Then("새 트랜잭션에서 먼저 커밋된 기록의 요약을 돌려주고 기록·링크가 늘지 않는다") {
                    val (competingId, summaryId, counts) = createDuringCompetingSave()

                    summaryId shouldBe competingId
                    counts shouldBe (1 to 0)
                }
            }
        }

        Given("존재하지 않는 사용자") {
            When("가져오기를 보내면") {
                val create =
                    suspend {
                        createImportService.createImport(RandomFixture.randomId(), ImportRequestFixture.createImportRequest())
                    }

                Then("예외를 반환한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.IMPORT_OWNER_NOT_FOUND.message) {
                        create()
                    }
                }
            }
        }
    })

private fun countLinkContentsOf(ownerId: Long): Long =
    (LinkContents innerJoin Links)
        .selectAll()
        .where { Links.ownerId eq ownerId }
        .count()
