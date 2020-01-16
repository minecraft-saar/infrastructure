package de.saar.minecraft.broker;

import java.io.Reader;
import java.util.ArrayList;
import org.yaml.snakeyaml.TypeDescription;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

public class BrokerConfiguration {
    private ArchitectServerAddress architectServer;
    private DatabaseAddress database;
    private int port;
    private int httpPort;
    private ArrayList<String> scenarios = new ArrayList<>();

    /**
     * Generates a BrokerConfiguration from the yaml data provided by the reader.
     */
    public static BrokerConfiguration loadYaml(Reader reader) {
        // prepare the YAML reader to read a list of strings
        Constructor constructor = new Constructor(BrokerConfiguration.class);
        TypeDescription brokerDesc = new TypeDescription(BrokerConfiguration.class);
        brokerDesc.addPropertyParameters("scenarios", String.class);
        constructor.addTypeDescription(brokerDesc);

        Yaml yaml = new Yaml(constructor);
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

    public ArrayList<String> getScenarios() {
        return scenarios;
    }

    public void setScenarios(ArrayList<String> scenarios) {
        this.scenarios = scenarios;
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

        @Override
        public String toString() {
            return hostname + ":" + port;
        }

        public ArchitectServerAddress() {
        }

        public ArchitectServerAddress(String hostname, int port) {
            this.hostname = hostname;
            this.port = port;
        }
    }

}
