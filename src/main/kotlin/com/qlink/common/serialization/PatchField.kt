package com.qlink.common.serialization

sealed interface PatchField<out T> {
    data object Absent : PatchField<Nothing>

    data class Present<T>(
        val value: T,
    ) : PatchField<T>
}
