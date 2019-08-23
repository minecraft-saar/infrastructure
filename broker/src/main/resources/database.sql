CREATE TABLE game_logs (
  id int(11) unsigned NOT NULL AUTO_INCREMENT PRIMARY KEY,
  gameid int(11),
  direction varchar(20) NOT NULL CHECK (direction IN ('FromClient','ToClient','FromArchitect','ToArchitect','PassToClient','PassToArchitect','None')),
  message_type varchar(100),
  message varchar(500),
  timestamp timestamp
);


CREATE TABLE games (
  id int(11) NOT NULL AUTO_INCREMENT PRIMARY KEY,
  client_ip varchar(200),
  player_name varchar(200),
  start_time TIMESTAMP,
  status varchar(20) NOT NULL CHECK (status IN ('created','running','finished')),
  architect_hostname varchar(100),
  architect_port int(11),
  architect_info varchar(500)
);
