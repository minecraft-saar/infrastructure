create schema if not exists MINECRAFT;

CREATE TABLE if not exists MINECRAFT.GAME_LOGS (
  id int(11) unsigned NOT NULL AUTO_INCREMENT PRIMARY KEY,
  gameid int(11),
  direction varchar(50),
  message_type varchar(50),
  message mediumtext,
  timestamp timestamp
);


CREATE TABLE if not exists MINECRAFT.GAMES (
  id int(11) NOT NULL AUTO_INCREMENT PRIMARY KEY,
  client_ip varchar(200),
  player_name varchar(200),
  start_time TIMESTAMP,
  status varchar(100),
  scenario varchar(200),
  architect_hostname varchar(100),
  architect_port int(11),
  architect_info varchar(500)
);
