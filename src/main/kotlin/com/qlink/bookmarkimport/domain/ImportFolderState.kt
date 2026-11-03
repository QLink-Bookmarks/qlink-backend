package com.qlink.bookmarkimport.domain

class ImportFolderState private constructor(
    val folder: ImportFolder,
    val state: ReversalFolderState,
) {
    constructor(
        folder: ImportFolder,
        isDeleted: Boolean,
        isShared: Boolean,
    ) : this(folder = folder, state = folder.reversalState(isDeleted = isDeleted, isShared = isShared))
}
