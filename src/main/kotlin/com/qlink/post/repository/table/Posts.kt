package com.qlink.post.repository.table

import com.qlink.post.domain.Post
import com.qlink.post.domain.PostType
import com.qlink.user.repository.table.Users
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder
import org.jetbrains.exposed.v1.javatime.CurrentTimestamp
import org.jetbrains.exposed.v1.javatime.timestamp
import kotlin.time.toKotlinInstant

object Posts : Table("posts") {
    val id = long("id").autoIncrement()
    val title = varchar("title", 100)
    val contents = text("contents")
    val type = enumerationByName<PostType>("type", 20)
    val authorId = reference("author_id", Users.id, onDelete = ReferenceOption.CASCADE)
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
    val updatedAt = timestamp("updated_at").defaultExpression(CurrentTimestamp)

    override val primaryKey = PrimaryKey(id)

    init {
        index("posts_type_id_idx", false, type, id)
        index("posts_author_id_idx", false, authorId)
    }
}

fun ResultRow.toPostDomain(): Post =
    Post(
        id = this[Posts.id],
        title = this[Posts.title],
        contents = this[Posts.contents],
        type = this[Posts.type],
        authorId = this[Posts.authorId],
        createdAt = this[Posts.createdAt].toKotlinInstant(),
        updatedAt = this[Posts.updatedAt].toKotlinInstant(),
    )

fun UpdateBuilder<*>.fromDomain(post: Post) {
    this[Posts.title] = post.title
    this[Posts.contents] = post.contents
    this[Posts.type] = post.type
    this[Posts.authorId] = post.authorId
}
