package com.danneu.pg

import com.danneu.pg.db.listStories

fun main(args: Array<String>) {
    val stories = listStories()
    stories.forEach { story ->
        println("[${story.createdAt.year}] ${story.title}")
        story.chapters.forEach { chapter ->
            println("- $chapter")
        }
    }
}
