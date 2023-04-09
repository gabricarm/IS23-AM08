package it.polimi.ingsw.server;

import com.google.gson.annotations.Expose;

/**
 * This class contains all the information for the correct setup of the server
 * It can also be loaded from file
 * Note that it is immutable
 */
public class LobbyServerConfig {

    /**
     * Integer containing the port that the lobby server will be open on
     */
    @Expose
    private final Integer serverPort;
    /**
     * String containing the server name that the client will refer to
     */
    @Expose
    private final String serverName;
    /**
     * Integer containing the first port that can be used for the creation of a game
     */
    @Expose
    private final Integer startingPort;


    /**
     * Constructor that takes all the parameters of the class
     * @param serverPort integer containing the information of the server port
     * @param serverName string containing the information of the server name
     * @param startingPort integer containing the information of the starting port
     */
    public LobbyServerConfig(Integer serverPort, String serverName, Integer startingPort){
        this.serverName=serverName;
        this.serverPort=serverPort;
        this.startingPort=startingPort;
    }

    /**
     * Getter of the server port
     * @return an integer
     */
    public Integer getServerPort() {
        return this.serverPort;
    }
    /**
     * Getter of the server name
     * @return a string
     */
    public String getServerName() {
        return this.serverName;
    }

    /**
     * Getter of the starting port of the server
     * @return an integer
     */
    public Integer getStartingPort() {
        return this.startingPort;
    }
}