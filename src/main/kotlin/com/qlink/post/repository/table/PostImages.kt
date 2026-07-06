package com.qlink.post.repository.table

import com.qlink.post.domain.PostImage
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder
import org.jetbrains.exposed.v1.javatime.CurrentTimestamp
import org.jetbrains.exposed.v1.javatime.timestamp
import kotlin.time.toKotlinInstant

object PostImages : Table("post_images") {
    val id = long("id").autoIncrement()
    val postId = reference("post_id", Posts.id, onDelete = ReferenceOption.CASCADE)
    val url = text("url")
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)

    override val primaryKey = PrimaryKey(id)

    init {
        index("post_images_post_id_idx", false, postId)
    }
}

fun ResultRow.toPostImageDomain(): PostImage =
    PostImage(
        id = this[PostImages.id],
        postId = this[PostImages.postId],
        url = this[PostImages.url],
        createdAt = this[PostImages.createdAt].toKotlinInstant(),
    )

fun UpdateBuilder<*>.fromDomain(image: PostImage) {
    this[PostImages.postId] = image.postId
    this[PostImages.url] = image.url
}
