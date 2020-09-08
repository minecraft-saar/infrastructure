CREATE TABLE if not exists GAME_LOGS (
  id int(11) unsigned NOT NULL AUTO_INCREMENT PRIMARY KEY,
  gameid int(11),
  direction varchar(50),
  message_type varchar(50),
  message mediumtext,
  timestamp TIMESTAMP(3)
);

CREATE TABLE if not exists GAMES (
  id int(11) NOT NULL AUTO_INCREMENT PRIMARY KEY,
  client_ip varchar(200),
  player_name varchar(200),
  start_time TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP(3),
  modified TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP(3),
  status varchar(100),
  scenario varchar(200),
  architect_hostname varchar(100),
  architect_port int(11),
  architect_info varchar(500)
);

CREATE TABLE if not exists QUESTIONNAIRES (
  id int(11) unsigned NOT NULL AUTO_INCREMENT PRIMARY KEY,
  gameid int(11),
  question VARCHAR(200),
  answer VARCHAR(5000),
  timestamp TIMESTAMP
);