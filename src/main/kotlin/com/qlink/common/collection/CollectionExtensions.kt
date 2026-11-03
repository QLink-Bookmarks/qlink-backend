package com.qlink.common.collection

fun <T> Collection<T>.isDistinct(): Boolean = size == toSet().size
