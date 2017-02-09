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


