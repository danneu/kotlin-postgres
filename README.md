
# kotlin-postgres

A quick experimental demo for how one might be able to read
database entities into kotlin classes while reusing the same
deserialization code on rows and nested json objects.

## The Problem

When you fetch records from the database, they might be top-level:

    SELECT * FROM stories
    SELECT * FROM chapters

But they also might be nested:

    SELECT
        *,
        ( SELECT json_agg(c.*)
          FROM chapters c
          WHERE c.story_id = stories.id
        ) chapters
    FROM stories

We want to be able to define `DbChapter` and `DbStory` such that they can
be created from rows but also nested JSON from the same code.

## The Idea

A `Record` class is defined in `db/core.kt` which is a sealed class
that contains a `Row` and `JsonObject`.

Our database entity classes simply need a `fromRecord(extractor: (Record) -> T?)`
function, and now they can be decoded from rows, json objects, and 
even nested arrays of json objects.

Here's our schema:

``` sql
DROP SCHEMA public CASCADE;
CREATE SCHEMA public;

CREATE TABLE stories (
  id         INT         PRIMARY KEY,
  title      TEXT        NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE chapters (
  id         INT         PRIMARY KEY,
  story_id   INT         NOT NULL REFERENCES stories(id),
  title      TEXT        NULL,
  text       TEXT        NOT NULL,
  position   INT         NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);


INSERT INTO stories (id, title) VALUES (1, 'The Boy and His Grue');
INSERT INTO chapters (id, story_id, position, title, text) VALUES
  (1, 1, 1, 'The Make Up', 'Deep in the dungeon, the boy found a grue.'),
  (2, 1, 2, 'The Break Up', 'The boy was eaten by the grue.');

INSERT INTO stories (id, title) VALUES (2, 'Another Story');
INSERT INTO chapters (id, story_id, position, title, text) VALUES
  (3, 2, 1, null, 'A premature ending.');
```

Here are our database entites we want to create from our queries:

``` kotlin
data class DbStory(val id: Int, val title: String, val chapters: List<DbChapter>, val createdAt: OffsetDateTime) {
    companion object {
        fun fromRecord(r: Record): DbStory {
            val id = r.int("id")!!
            val title = r.string("title")!!
            val chapters = r.jsonRecords("chapters")!!.map { DbChapter.fromRecord(it) }   // <-- Notice
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
```

Here's our query function:

``` kotlin
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
```

## Demo

``` kotlin
fun main(args: Array<String>) {
    val stories = listStories()
    stories.forEach { story ->
        println("[${story.createdAt.year}] ${story.title}")
        story.chapters.forEach { chapter ->
            println("- $chapter")
        }
    }
}
```

Output:

```
[2017] The Boy and His Grue
- The Make Up: "Deep in the dungeon, the boy found a grue."
- The Break Up: "The boy was eaten by the grue."
[2017] Another Story
- Chapter 1: "A premature ending."
```
