package com.qlink.bookmarkimport.domain.service

import com.qlink.bookmarkimport.domain.ImportSource
import com.qlink.bookmarkimport.domain.ImportSourceFolder
import com.qlink.common.text.takeKeepingSurrogatePairs

internal class NumberedFolderNames(
    takenNames: List<String>,
) {
    private val taken = takenNames.mapTo(HashSet()) { ImportSourceFolder.comparableNameOf(it) }
    private val assigned = HashMap<String, String>()

    fun of(name: String): String = assigned.getOrPut(ImportSourceFolder.comparableNameOf(name)) { next(name) }

    fun next(name: String): String =
        generateSequence(2) { it + 1 }
            .map { numbered(name.trim(), it) }
            .first { taken.add(ImportSourceFolder.comparableNameOf(it)) }

    private fun numbered(
        name: String,
        number: Int,
    ): String {
        val suffix = " $number"

        return name.takeKeepingSurrogatePairs(ImportSource.FOLDER_NAME_MAX_LENGTH - suffix.length) + suffix
    }
}
