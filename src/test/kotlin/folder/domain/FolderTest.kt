package folder.domain

import com.qlink.folder.domain.Folder
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

class FolderTest :
    StringSpec({
        "creates a folder domain model" {
            val now = Clock.System.now()
            val folder =
                Folder(
                    id = 1,
                    ownerId = 2,
                    name = "Work",
                    emoji = ":folder:",
                    sharedAt = now,
                    createdAt = now,
                    updatedAt = now,
                )

            folder.ownerId shouldBe 2
            folder.name shouldBe "Work"
            folder.copy() shouldBe folder
            folder.copy(id = 2) shouldNotBe folder
            folder.copy(ownerId = 3) shouldNotBe folder
            folder.copy(name = "Home") shouldNotBe folder
            folder.copy(emoji = null) shouldNotBe folder
            folder.copy(sharedAt = null) shouldNotBe folder
            folder.copy(createdAt = now + 1.seconds) shouldNotBe folder
            folder.copy(updatedAt = now + 2.seconds) shouldNotBe folder
            folder.equals("folder") shouldBe false
            folder.hashCode() shouldBe folder.copy().hashCode()
        }
    })
