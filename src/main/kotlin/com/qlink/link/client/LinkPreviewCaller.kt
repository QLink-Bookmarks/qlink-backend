package com.qlink.link.client

enum class LinkPreviewCaller(
    val tag: String,
) {
    LINK_PREVIEW("link_preview"),
    FAVICON_UPLOAD("favicon_upload"),
    IMPORT_THUMBNAIL("import_thumbnail"),
}
