package com.qlink.bookmarkimport.domain

class ImportLinkState private constructor(
    val link: ImportLink,
    val state: ReversalLinkState,
) {
    constructor(
        link: ImportLink,
        isDeleted: Boolean,
        isEdited: Boolean,
    ) : this(link = link, state = link.reversalState(isDeleted = isDeleted, isEdited = isEdited))
}
