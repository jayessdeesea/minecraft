-- A mission is written in whoever gave it's words, and a model's ran to 257 characters: the row wasn't taken, and the
-- platoon leader's whole decision - every squad's orders - went with it, each cycle, for as long as the model said it so.
-- Room for what's said: the mission, the task and the why.

ALTER TABLE objective ALTER COLUMN mission SET DATA TYPE VARCHAR(4096);

ALTER TABLE objective ALTER COLUMN task SET DATA TYPE VARCHAR(1024);

ALTER TABLE objective ALTER COLUMN why SET DATA TYPE VARCHAR(4096);
