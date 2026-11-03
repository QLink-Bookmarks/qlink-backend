package com.qlink.bookmarkimport.route

import com.qlink.common.scroll.DEFAULT_SCROLL_SIZE
import io.ktor.resources.Resource

@Resource("/")
class ImportResources {
    @Resource("import-previews")
    class Previews(
        val parent: ImportResources = ImportResources(),
    )

    @Resource("imports")
    class Imports(
        val parent: ImportResources = ImportResources(),
        val cursor: String? = null,
        val size: Int = DEFAULT_SCROLL_SIZE,
    ) {
        @Resource("{id}")
        class ById(
            val parent: Imports = Imports(),
            val id: Long,
        ) {
            @Resource("reversal-preview")
            class ReversalPreview(
                val parent: ById,
            )

            @Resource("reversals")
            class Reversals(
                val parent: ById,
            )
        }
    }
}
