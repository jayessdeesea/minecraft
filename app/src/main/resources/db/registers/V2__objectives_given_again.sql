-- An objective given again - the same mission, a later day - is a row of its own: no unique (mission, step).
-- The table rebuilt without the constraint V1 gave it, rows kept.

CREATE TABLE objective_v2 (
    id          BIGINT       AUTO_INCREMENT PRIMARY KEY,
    mission     VARCHAR(256) NOT NULL,
    step        INT          NOT NULL,
    task        VARCHAR(256) NOT NULL,
    chunk_x     INT          NOT NULL,
    chunk_z     INT          NOT NULL,
    squad       VARCHAR(64),
    state       VARCHAR(16)  NOT NULL,
    changed_at  BIGINT       NOT NULL,
    why         VARCHAR(1024)
);

-- New ids, in the order they were kept: nothing refers to an objective's id from outside the register.
INSERT INTO objective_v2 (mission, step, task, chunk_x, chunk_z, squad, state, changed_at, why)
    SELECT mission, step, task, chunk_x, chunk_z, squad, state, changed_at, why FROM objective ORDER BY id;

DROP TABLE objective;

ALTER TABLE objective_v2 RENAME TO objective;

CREATE INDEX objective_chunk ON objective (chunk_x, chunk_z);

CREATE INDEX objective_squad ON objective (squad, state);
