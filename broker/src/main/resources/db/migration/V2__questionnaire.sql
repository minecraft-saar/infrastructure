CREATE TABLE if not exists MINECRAFT.QUESTIONNAIRES (
    id int(11) unsigned NOT NULL AUTO_INCREMENT PRIMARY KEY,
    gameid int(11),
    question VARCHAR(200),
    answer VARCHAR(5000),
    timestamp TIMESTAMP
);
