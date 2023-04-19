package it.polimi.ingsw.network.client;

import it.polimi.ingsw.controller.exceptions.InvalidNicknameException;
import it.polimi.ingsw.controller.exceptions.InvalidMoveException;
import it.polimi.ingsw.gameInfo.GameInfo;
import it.polimi.ingsw.gameInfo.State;
import it.polimi.ingsw.model.Position;
import it.polimi.ingsw.network.server.RmiServerInterface;
import it.polimi.ingsw.network.server.exceptions.*;
import it.polimi.ingsw.network.server.RMILobbyServerInterface;
import it.polimi.ingsw.network.server.constants.ServerConstants;
import it.polimi.ingsw.network.server.exceptions.*;
import it.polimi.ingsw.view.View;

import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.List;

public class RmiClient extends UnicastRemoteObject implements Client, RmiClientInterface{
    private String nickname;

    private int lobbyPort;
    private String LobbyServerName = ServerConstants.LOBBY_SERVER;
    private final String ipToConnect;

    private RmiServerInterface matchServer;
    private RMILobbyServerInterface lobbyServer;

    private Registry lobbyRegistry;

    private View view;

    private Object lock = new Object();


    /**
     * This method is the constructor of RmiClient
     * @param nickname
     * @param fV : this will become the true view
     * @param ipToConnect ip of the server to connect to
     * @throws RemoteException
     * @throws NotBoundException
     */
    public RmiClient(String nickname, View fV, String ipToConnect, Integer lobbyPort) throws NotBoundException, InterruptedException, RemoteException {
        super();
        this.view = fV;
        this.nickname = nickname;
        this.ipToConnect=ipToConnect;
        this.lobbyPort=lobbyPort;

        //System.setProperty("java.rmi.server.hostname", "192.168.43.54");
        // to comment in case of test without LobbyServer
        this.connectToLobbyServer();
    }

    /**
     * This method looks up the registry of the lobby server
     * If it doesn't found it he waits for 5 second
     * @throws RemoteException
     * @throws NotBoundException
     */
    private void connectToLobbyServer() throws InterruptedException {
        while(true) {
            try {
                System.out.println("Looking up the registry for LobbyServer at "+this.ipToConnect+":"+this.lobbyPort);
                // swap 'localhost' with the server ip when trying to connect with two different machines
                this.lobbyRegistry = LocateRegistry.getRegistry(this.ipToConnect, this.lobbyPort);
                this.lobbyServer = (RMILobbyServerInterface) this.lobbyRegistry.lookup(LobbyServerName);
                break;
            } catch (Exception e) {
                System.out.println("Registry not found");
                Thread.sleep(5000);
            }
        }
    }

    /**
     * This method updates the view with new information
     * @param newState : the new state of the game
     * @param newInfo : the new info for the view
     * @throws RemoteException
     */
    @Override
    public void update(State newState, GameInfo newInfo) throws RemoteException {
        this.view.update(newState, newInfo);
    }

    /**
     * This method lets the player choose his nickname
     * @param nick
     * @return true if nickname is available
     * @throws RemoteException
     */
    @Override
    public boolean chooseNickname(String nick) {
        boolean flag = false;
        try {
            flag = lobbyServer.chooseNickname(nick);
        } catch (ExistentNicknameExcepiton | IllegalNicknameException e) {
            flag = false;
        } catch (RemoteException e) {
            System.out.println("Remote exception from chooseNickname");
            e.printStackTrace();
            this.gracefulDisconnection();
        }

        if (flag) this.nickname = nick;
        return flag;
    }

    /**
     * This method lets the player make a move
     * @param pos : a List of positions
     * @param col : the column of the shelf
     * @throws RemoteException
     */
    public void makeMove(List<Position> pos, int col) throws InvalidNicknameException, InvalidMoveException {
        try {
            this.matchServer.makeMove(pos, col, nickname);
        } catch (RemoteException e) {
            System.out.println("Remote exception from makeMove");
            this.gracefulDisconnection();
        }
    }

    /**
     * This method lets a player create a game and choose the available player slots
     * @param num : player slots
     * @throws RemoteException
     * @throws NotBoundException
     */
    public void createGame(int num) throws NonExistentNicknameException, AlreadyInGameException {
        try {
            String matchServerName = this.lobbyServer.createGame(num, nickname, this);
            this.connectToMatchServer(matchServerName);
        } catch (RemoteException e) {
            System.out.println("Remote exception from createGame");
            this.gracefulDisconnection();
        } catch (NotBoundException e) {
            System.out.println("Trying to lock up an unbound registry");
            throw new RuntimeException(e);
        }
    }

    /**
     * This method lets a player join a game
     * @throws RemoteException
     * @throws NotBoundException
     */
    public void joinGame() throws NoGamesAvailableException, NonExistentNicknameException, AlreadyInGameException {
        try {
            String matchServerName = this.lobbyServer.joinGame(nickname, this);
            this.connectToMatchServer(matchServerName);
        } catch (RemoteException e) {
            System.out.println("Remote exception from joinGame");
            this.gracefulDisconnection();
        } catch (NotBoundException e) {
            System.out.println("Trying to lock up an unbound registry");
            throw new RuntimeException(e);
        }

    }

    /**
     * This method connects to the MatchServer using information available in the parameter
     * it also starts a thread that pings the server every 1 second
     * @param matchServerName : name of the server to connect
     * @throws RemoteException
     * @throws NotBoundException
     */
    private void connectToMatchServer(String matchServerName) throws RemoteException, NotBoundException {
//        System.out.println("Looking up the registry for MatchServer");
//        System.out.println("Name: "+c.getRegistryName()+" Port: "+c.getRegistryPort());
//        Registry registry = LocateRegistry.getRegistry("192.168.43.4", c.getRegistryPort());
        this.matchServer = (RmiServerInterface) this.lobbyRegistry.lookup(matchServerName);

        // new thread to ping server
        Thread t = new Thread(() -> {
            synchronized (lock) {
                System.out.println("New Ping Thread starting");
                while (true) {
                    try {
                        this.pingServer();
                        lock.wait(ServerConstants.PING_TIME);

                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    } catch (RemoteException e) {
                        this.gracefulDisconnection();
                        break;
                    }
                }
            }
        });
        t.start();
    }

    /**
     * This method lets the server check if the client is alive
     * @return true
     * @throws RemoteException
     */
    public boolean isAlive() throws RemoteException {return true;}

    /**
     * This method lets the client send a message privately only to someone
     * @param message
     * @param receiver : the one that is supposed to receive the message
     * @throws RemoteException
     */
    public void messageSomeone(String message, String receiver){
        try {
            this.matchServer.messageSomeone(message, this.nickname, receiver);
        } catch (RemoteException e) {
            System.out.println("Remote exception from chat");
            this.gracefulDisconnection();
        }
    }

    /**
     * This method lets the client send a message to every other client connected to the game
     * @param message
     * @throws RemoteException
     */
    public void messageAll(String message){
        try {
            this.matchServer.messageAll(message, this.nickname);
        } catch (RemoteException e) {
            System.out.println("Remote exception from chat");
            this.gracefulDisconnection();
        }

    }

    /**
     * This method lets the server ask a client for his nickname
     * @return the nickname of the client
     * @throws RemoteException
     */
    public String name() throws RemoteException{
        return this.nickname;
    }

    /**
     * This method notifies the view that a message has arrived
     * @param message
     * @throws RemoteException
     */
    public void receiveMessage(String message) throws RemoteException{
        this.view.displayChatMessage(message);
    }

    /**
     * This method pings the server
     * @throws RemoteException
     */
    private void pingServer() throws RemoteException {
        this.matchServer.isAlive();
    }

    /**
     * This manages the disconnection
     */
    private void gracefulDisconnection(){
        System.out.println("Initializing graceful disconnection");
        view.update(State.GRACEFULDISCONNECTION, null);
    }

}