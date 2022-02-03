package de.saar.minecraft.broker;

import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import org.yaml.snakeyaml.TypeDescription;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

/** contains all the information stated in the broker-config.yaml file with getters and setters.**/
public class BrokerConfiguration {
    private List<ArchitectServerAddress> architectServers = new ArrayList<>();
    private DatabaseAddress database;
    private int port;
    private int httpPort;
    private List<String> scenarios = new ArrayList<>();
    private boolean useInternalQuestionnaire = true;

    /**
     * Generates a BrokerConfiguration from the yaml data provided by the reader.
     * @param reader reader for the config
     * @return the broker config
     */
    public static BrokerConfiguration loadYaml(Reader reader) {
        // prepare the YAML reader to read a list of strings
        Constructor constructor = new Constructor(BrokerConfiguration.class);
        TypeDescription brokerDesc = new TypeDescription(BrokerConfiguration.class);

        brokerDesc.addPropertyParameters("scenarios", String.class);
        brokerDesc.addPropertyParameters("architects", ArchitectServerAddress.class);
        constructor.addTypeDescription(brokerDesc);

        Yaml yaml = new Yaml(constructor);
        return yaml.loadAs(reader, BrokerConfiguration.class);
    }

    /** getter for architect servers.
     * @return list of all active architectservers **/
    public List<ArchitectServerAddress> getArchitectServers() {
        return architectServers;
    }

    /** setter for architect servers.
     * @param architectServers  list of all active architectservers **/
    public void setArchitectServers(List<ArchitectServerAddress> architectServers) {
        this.architectServers = architectServers;
    }

    /** getter for database.
     * @return the database **/
    public DatabaseAddress getDatabase() {
        return database;
    }

    /** setter for database.
     * @param database the database **/
    public void setDatabase(DatabaseAddress database) {
        this.database = database;
    }

    /** getter for port.
     * @return the port **/
    public int getPort() {
        return port;
    }

    /** setter for port.
     * @param port  the port **/
    public void setPort(int port) {
        this.port = port;
    }

    /** getter for http port.
     * @return the http port **/
    public int getHttpPort() {
        return httpPort;
    }

    /** setter for http port.
     * @param httpPort the http port **/
    public void setHttpPort(int httpPort) {
        this.httpPort = httpPort;
    }

    /** getter for the secnarios.
     * @return the scenarios **/
    public List<String> getScenarios() {
        return scenarios;
    }

    /** setter for the secnarios.
     * @param scenarios  the scenarios **/
    public void setScenarios(List<String> scenarios) {
        this.scenarios = scenarios;
    }

    /** getter for useInternalQuestionnaire.
     * @return useInternalQuestionnaire **/
    public boolean getUseInternalQuestionnaire() {
        return useInternalQuestionnaire;
    }

    /** setter for useInternalQuestionnaire.
     * @param useInternalQuestionnaire  useInternalQuestionnaire **/
    public void setUseInternalQuestionnaire(boolean useInternalQuestionnaire) {
        this.useInternalQuestionnaire = useInternalQuestionnaire;
    }

    /** database access Data. **/
    public static class DatabaseAddress {
        private String url;
        private String username;
        private String password;
        private String sqlDialect = "MYSQL";

        /** getter for database URL.
         * @return the url**/
        public String getUrl() {
            return url;
        }

        /** setter for database URL.
         * @param url  the url**/
        public void setUrl(String url) {
            this.url = url;
        }

        /** getter for database username.
         * @return username**/
        public String getUsername() {
            return username;
        }

        /** setter for database username.
         * @param username the username**/
        public void setUsername(String username) {
            this.username = username;
        }

        /** getter for database password.
         * @return  the password**/
        public String getPassword() {
            return password;
        }

        /** setter for database password.
         * @param password the password**/
        public void setPassword(String password) {
            this.password = password;
        }

        /** getter for sql dialect used.
         * @return the dialect used**/
        public String getSqlDialect() {
            return sqlDialect;
        }

        /** setter for sql dialect used.
         * @param sqlDialect  the dialect used**/
        public void setSqlDialect(String sqlDialect) {
            this.sqlDialect = sqlDialect;
        }
    }

    /** info about port and host of architect servers. **/
    public static class ArchitectServerAddress {
        private String hostname;
        private int port;

        /** getter for hostname.
         * @return  the hostname**/
        public String getHostname() {
            return hostname;
        }

        /** setter for hostname.
         * @param hostname the hostname**/
        public void setHostname(String hostname) {
            this.hostname = hostname;
        }

        /** getter for port.
         * @return the port **/
        public int getPort() {
            return port;
        }

        /** setter for port.
         * @param port  the port **/
        public void setPort(int port) {
            this.port = port;
        }

        @Override
        public String toString() {
            return hostname + ":" + port;
        }

        /**
         * constructor.
         */
        public ArchitectServerAddress() {
        }

        /** constructor.
         * @param hostname the hostname
         * @param port the port**/
        public ArchitectServerAddress(String hostname, int port) {
            this.hostname = hostname;
            this.port = port;
        }
    }

}
