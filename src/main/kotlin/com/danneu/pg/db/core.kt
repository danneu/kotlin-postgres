package com.danneu.pg.db

import com.beust.klaxon.JsonArray
import com.beust.klaxon.Parser
import com.beust.klaxon.array
import com.beust.klaxon.int
import com.beust.klaxon.string
import com.zaxxer.hikari.HikariDataSource
import kotliquery.HikariCP
import kotliquery.Query
import kotliquery.action.ResultQueryActionBuilder
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter


val dataSource: HikariDataSource = HikariCP.default("jdbc:postgresql://localhost:5432/kotlin-postgres", "username", "password")


sealed class Record {
    abstract fun string(key: String): String?
    abstract fun int(key: String): Int?
    abstract fun jsonRecords(key: String): List<Record>?
    abstract fun offsetDateTime(key: String): OffsetDateTime?

    class Row(val underlying: kotliquery.Row) : Record() {
        override fun string(key: String) = underlying.string(key)
        override fun int(key: String) = underlying.int(key)
        override fun offsetDateTime(key: String) = underlying.offsetDateTimeOrNull(key)
        override fun jsonRecords(key: String) = (Parser().parse(underlying.binaryStream(key)) as JsonArray<*>)
            .filterIsInstance<com.beust.klaxon.JsonObject>()
            .map { Record.fromJsonObject(it) }
    }

    class JsonObject(val underlying: com.beust.klaxon.JsonObject) : Record() {
        override fun string(key: String) = underlying.string(key)
        override fun int(key: String) = underlying.int(key)
        override fun offsetDateTime(key: String) = underlying.string(key)?.let { it ->
            OffsetDateTime.parse(it, DateTimeFormatter.ISO_DATE_TIME)
        }
        override fun jsonRecords(key: String) = underlying.array<com.beust.klaxon.JsonObject>(key)
            ?.map { Record.fromJsonObject(it) }
    }

    companion object {
        fun fromRow(row: kotliquery.Row): Record = Row(row)
        fun fromJsonObject(obj: com.beust.klaxon.JsonObject): Record = JsonObject(obj)
    }
}

fun <A> Query.mapRecord(extractor: (Record) -> A?): ResultQueryActionBuilder<A> {
    return this.map { extractor(Record.fromRow(it)) }
}

// Our database entities

data class DbStory(val id: Int, val title: String, val chapters: List<DbChapter>, val createdAt: OffsetDateTime) {
    companion object {
        fun fromRecord(r: Record): DbStory {
            val id = r.int("id")!!
            val title = r.string("title")!!
            val chapters = r.jsonRecords("chapters")!!.map { DbChapter.fromRecord(it) }
            val createdAt = r.offsetDateTime("created_at")!!
            return DbStory(id, title, chapters, createdAt)
        }
    }
}

data class DbChapter(val id: Int, val position: Int, val storyId: Int, val title: String?, val text: String, val createdAt: OffsetDateTime) {
    override fun toString() = "${title ?: "Chapter $position"}: \"${text.take(140)}\""

    companion object {
        fun fromRecord(r: Record): DbChapter {
            val id = r.int("id")!!
            val storyId = r.int("story_id")!!
            val title = r.string("title")
            val text = r.string("text")!!
            val position = r.int("position")!!
            val createdAt = r.offsetDateTime("created_at")!!
            return DbChapter(id, position, storyId, title, text, createdAt)
        }
    }
}

// Our application queries which return our database entities

fun listStories(): List<DbStory> {
    return using(sessionOf(dataSource)) { session ->
        queryOf("""
            SELECT
                *,
                ( SELECT json_agg(c.*)
                  FROM chapters c
                  WHERE c.story_id = stories.id
                ) chapters
            FROM stories
        """)
            .mapRecord { DbStory.fromRecord(it) }
            .asList
            .runWithSession(session)
    }
}
