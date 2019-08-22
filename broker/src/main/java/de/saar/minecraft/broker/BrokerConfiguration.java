package de.saar.minecraft.broker;

import org.yaml.snakeyaml.Yaml;

import java.io.Reader;

public class BrokerConfiguration {
    private ArchitectServerAddress architectServer;
    private DatabaseAddress database;
    private int port;
    private int httpPort;

    public static BrokerConfiguration loadYaml(Reader reader) {
        Yaml yaml = new Yaml();
        BrokerConfiguration config = yaml.loadAs(reader, BrokerConfiguration.class);
        return config;
    }

    public ArchitectServerAddress getArchitectServer() {
        return architectServer;
    }

    public void setArchitectServer(ArchitectServerAddress architectServer) {
        this.architectServer = architectServer;
    }

    public DatabaseAddress getDatabase() {
        return database;
    }

    public void setDatabase(DatabaseAddress database) {
        this.database = database;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public int getHttpPort() {
        return httpPort;
    }

    public void setHttpPort(int httpPort) {
        this.httpPort = httpPort;
    }

    public static class DatabaseAddress {
        private String url;
        private String username;
        private String password;
        private String sqlDialect = "MYSQL";

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getSqlDialect() {
            return sqlDialect;
        }

        public void setSqlDialect(String sqlDialect) {
            this.sqlDialect = sqlDialect;
        }
    }

    public static class ArchitectServerAddress {
        private String hostname;
        private int port;

        public String getHostname() {
            return hostname;
        }

        public void setHostname(String hostname) {
            this.hostname = hostname;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }
    }

}
