package com.qlink.common.number

fun Long.foldToInt(): Int = (this xor (this ushr Int.SIZE_BITS)).toInt()
