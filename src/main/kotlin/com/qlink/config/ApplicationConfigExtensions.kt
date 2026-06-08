package com.qlink.config

import io.ktor.server.config.ApplicationConfig

fun ApplicationConfig.string(path: String): String = property(path).getString()

fun ApplicationConfig.optionalString(path: String): String? = propertyOrNull(path)?.getString()

fun ApplicationConfig.boolean(path: String): Boolean = property(path).getString().toBoolean()

fun ApplicationConfig.int(path: String): Int = property(path).getString().toInt()

fun ApplicationConfig.optionalInt(path: String): Int? = propertyOrNull(path)?.getString()?.toInt()

fun ApplicationConfig.stringList(path: String): List<String> = property(path).getList()

fun ApplicationConfig.optionalStringList(path: String): List<String> = propertyOrNull(path)?.getList().orEmpty()
