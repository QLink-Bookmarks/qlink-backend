package support.fixture

import com.qlink.link.domain.Link
import com.qlink.link.domain.SourceType
import kotlin.random.Random

object LinkFixture {
    fun createRandomLinkOf(
        ownerId: Long,
        folderId: Long? = null,
    ): Link =
        Link(
            id = RandomFixture.randomId(),
            ownerId = ownerId,
            folderId = folderId,
            url = RandomFixture.randomUrl(),
            title = RandomFixture.randomSentenceWithMax(300),
            summary = RandomFixture.randomSentenceWithMax(1000),
            thumbnailUrl = RandomFixture.randomUrl(),
            sourceType = SourceType.entries[Random.nextInt(SourceType.entries.size)],
            tags = RandomFixture.randomSentenceList(),
        )
}
