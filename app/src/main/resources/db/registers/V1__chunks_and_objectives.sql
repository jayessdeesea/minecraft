-- The platoon leader's registers (app.Registers): what the force has made of each chunk, and its objectives.

CREATE TABLE chunk (
    chunk_x     INT          NOT NULL,
    chunk_z     INT          NOT NULL,
    kind        VARCHAR(32)  NOT NULL,
    level       INT          NOT NULL,
    stage       VARCHAR(64),
    measured_at BIGINT       NOT NULL,
    PRIMARY KEY (chunk_x, chunk_z)
);

CREATE TABLE objective (
    id          BIGINT       AUTO_INCREMENT PRIMARY KEY,
    mission     VARCHAR(256) NOT NULL,
    step        INT          NOT NULL,
    task        VARCHAR(256) NOT NULL,
    chunk_x     INT          NOT NULL,
    chunk_z     INT          NOT NULL,
    squad       VARCHAR(64),
    state       VARCHAR(16)  NOT NULL,
    changed_at  BIGINT       NOT NULL,
    why         VARCHAR(1024),
    UNIQUE (mission, step)
);

CREATE INDEX objective_chunk ON objective (chunk_x, chunk_z);
