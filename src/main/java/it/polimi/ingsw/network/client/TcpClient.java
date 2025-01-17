package it.polimi.ingsw.network.client;

import it.polimi.ingsw.network.client.clientLocks.Lock;
import it.polimi.ingsw.network.client.exceptions.ConnectionError;
import it.polimi.ingsw.controller.exceptions.InvalidMoveException;
import it.polimi.ingsw.controller.exceptions.InvalidNicknameException;
import it.polimi.ingsw.gameInfo.GameInfo;
import it.polimi.ingsw.gameInfo.State;
import it.polimi.ingsw.model.Position;
import it.polimi.ingsw.network.client.exceptions.GameEndedException;
import it.polimi.ingsw.network.messages.clientMessages.*;
import it.polimi.ingsw.network.messages.serverMessages.*;
import it.polimi.ingsw.network.server.Lobby;
import it.polimi.ingsw.network.server.exceptions.*;
import it.polimi.ingsw.constants.ServerConstants;
import it.polimi.ingsw.network.messages.Message;
import it.polimi.ingsw.view.View;

import java.io.*;
import java.util.List;

import java.net.*;

/**
 * This class represents a client that uses the tcp connection protocol
 */
public class TcpClient implements Client{
    /**
     * This attribute is the nickname of the player
     */
    private String nickname;

    /**
     * This attribute is the socket on which the communication will occur
     */
    private Socket socket;

    /**
     * This attribute is the object output stream
     */
    private ObjectOutputStream objectOutputStream;

    /**
     * This attribute is the object input stream
     */
    private ObjectInputStream objectInputStream;

    /**
     * This attribute is the View
     */
    private final View view;

    /**
     * This attribute is a lock on which all the actions will synchronize
     */
    private final Lock actionLock = new Lock();

    /**
     * This attribute is a lock on which the ping will synchronize
     */
    private final Lock pingThreadLock = new Lock();

    /**
     * If this flag is true the client has to ping the server
     */
    private boolean toPing = true;
    /**
     * If this flag is true the client will listen for inbound messages
     */
    private boolean listeningForMessages = true;
    /**
     * If this flag is true the client is online
     */
    private boolean isClientOnline = true;

    /**
     * If this flag is true the client is mute
     */
    private final boolean mute = false;

    /**
     * If this flag is true the client prints only essential messages
     */
    private final boolean essential = true;


    /**
     * Constructor of TcpClient
     * @param nickname: nickname of the player
     * @param view: the view of the player
     * @param serverIp: the ip of the server
     * @param lobbyPort: the port of the lobby server
     * @throws InterruptedException if the thread is interrupted
     * @throws ConnectionError if the connection fails
     */
    public TcpClient(String nickname, View view, String serverIp, Integer lobbyPort) throws InterruptedException, ConnectionError {
        super();
        this.view = view;
        this.nickname = nickname;

        this.connectToLobbyServer(serverIp, lobbyPort);
    }


    /**
     * This method connects the client to the lobby server, if it doesn't find a server it waits for 5 seconds
     * @param serverIp: the ip of the server
     * @param lobbyPort: the port of the server
     * @throws InterruptedException
     * @throws ConnectionError if the connection fails
     */
    private void connectToLobbyServer(String serverIp, Integer lobbyPort) throws InterruptedException, ConnectionError {
        while (true) {
            try {
                this.socket = new Socket(serverIp, lobbyPort);
                if (!mute && !essential) System.out.println("Tcp connection established");
                break;
            } catch (IOException e) {
                if (!mute) System.out.println("Server not found");
                Thread.sleep(ServerConstants.CLIENT_SLEEPING_TIME);
            }
        }

        // Opening output streams
        try {
            if (!mute && !essential) System.out.println("Opening Output Streams");
            this.objectOutputStream = new ObjectOutputStream(socket.getOutputStream());
        } catch (IOException e) {
            if (!mute && !essential) System.out.println("Failed opening Output Streams");
            this.gracefulDisconnection(true);
            throw new ConnectionError();
        }

        // Here we set a timeout for the socket
        try {
            this.socket.setSoTimeout(ServerConstants.PING_TIME+ServerConstants.TCP_WAIT_TIME+1000);
        } catch (SocketException e) {
            if(!mute) System.out.println("SocketException from setSoTimeout");
            this.gracefulDisconnection(true);
        }

        // Thread to receive messages from server
        this.createInboundMessagesThread();

        // Thread to ping server
        this.createPingThread();
    }


    /**
     * This method creates a thread to receive inbound messages
     */
    private void createInboundMessagesThread(){
        if (!mute && !essential) System.out.println("New MessagesListener Thread starting");
        if (!mute && !essential) System.out.println("Opening Input Streams");
        Thread t = new Thread(() -> {
            try {
                this.objectInputStream = new ObjectInputStream(socket.getInputStream());
            } catch (IOException e) {
                if (!mute && !essential) System.out.println("Failed opening Input Streams");
                this.gracefulDisconnection(true);
            }

            while(listeningForMessages){
                try {
                    Message message = (Message) objectInputStream.readObject();
                    this.manageInboundTcpMessages(message);

                } catch (SocketTimeoutException e) {
                    if (listeningForMessages) {
                        if (!mute && !essential) System.out.println("SocketTimeout Exception InboundMessagesThread");
                        this.gracefulDisconnection(true);
                    }
                } catch (IOException e) {
                    if (listeningForMessages){
                        if (!mute && !essential) System.out.println("IOException from InboundMessagesThread");
                        // e.printStackTrace();
                        this.gracefulDisconnection(true);
                    }
                } catch (ClassNotFoundException e) {
                    if (listeningForMessages){
                        if (!mute && !essential) System.out.println("ClassNotFoundException from InboundMessagesThread");
                        this.gracefulDisconnection(true);
                    }
                }
            }

        });
        t.start();

    }


    /**
     * This method creates a thread to ping the server
     */
    private void createPingThread(){
        if (!mute && !essential) System.out.println("New Ping Thread starting");

        // The client keeps the heartbeat
        Thread t = new Thread(() -> {
            synchronized (pingThreadLock) {
                while (toPing) {
                    try {
                        this.sendTcpMessage(new PingClientMessage(this.nickname));
                        pingThreadLock.wait(ServerConstants.PING_TIME);
                    } catch (InterruptedException e) {
                        if (!mute && !essential) System.out.println("Interrupted exception from PingThread");
                        this.gracefulDisconnection(true);
                    }
                }

            }
        });
        t.start();
    }


    /**
     * This method manages the whole tcp conversation by trying to send a message and waiting for a response
     * This method synchronizes on the lock passed as parameter to manage the conversation
     * This method also stop the client from pinging the thread till the conversation ends
     * @param lock the lock used for synchronization
     * @param message the message
     * @return the retrieved message
     * @throws ConnectionError if the connection fails
     */
    private Message manageTcpConversation(Lock lock, Message message) throws ConnectionError {
        // Alternative version
        try {
            // we block the ping thread from sending new pings till the conversation ends
            synchronized (pingThreadLock) {
                synchronized (lock) {
                    this.sendTcpMessage(message);
                    long time1 = System.currentTimeMillis();

                    // Version 1: infinite wait
                    while (lock.toWait()) lock.wait();
                    // Version 2: finite wait
                    //lock.wait(ServerConstants.TCP_WAIT_TIME);

                    long time2 = System.currentTimeMillis();

                    if(!mute && !essential) System.out.println("Waited response for: "+ (time2-time1) + " ms");

                    return this.retrieveMessageFromLock(lock);
                }
            }
        }
        catch (ConnectionError e) {
            if (!mute && !essential) System.out.println("Connection error from "+ message.toString());
            this.gracefulDisconnection(true);
            throw new ConnectionError();
        } catch (InterruptedException e) {
            if (!mute && !essential) System.out.println("Interrupted Exception from "+ message.toString());
            this.gracefulDisconnection(true);
            throw new ConnectionError();
        }



        // Old version
//        // This locks exists only in the method
//        Lock manageTcpConversationLock = new Lock();
//
//        // here we create a thread that manages the inbound message
//        Thread t = new Thread(()->{
//            try {
//                synchronized (manageTcpConversationLock) {
//                    synchronized (lock) {
//                        this.sendTcpMessage(message);
//                        long time1 = System.currentTimeMillis();
//
//                        // Version 1: infinite wait
//                        while (lock.toWait()) lock.wait();
//                        // Version 2: finite wait
//                        //lock.wait(ServerConstants.TCP_WAIT_TIME);
//
//                        long time2 = System.currentTimeMillis();
//
//                        if(!mute && !essential) System.out.println("Waited response for: "+ (time2-time1) + " ms");
//
//                        manageTcpConversationLock.setToWait(false);
//                        manageTcpConversationLock.notifyAll();
//                    }
//                }
//            } catch (InterruptedException e) {
//                if (!mute && !essential) System.out.println("Interrupted Exception from manageTcpConversation");
//                this.gracefulDisconnection(true);
//            }
//        });
//
//        // Here we start the thread and release the lock
//        try {
//            synchronized (manageTcpConversationLock) {
//                // we block the ping thread from sending new pings till the conversation ends
//                synchronized (pingThreadLock) {
//                    t.start();
//
//                    manageTcpConversationLock.setToWait(true);
//                    while (manageTcpConversationLock.toWait()) manageTcpConversationLock.wait();
//
//                    return this.retrieveMessageFromLock(lock);
//                }
//            }
//        }
//         catch (ConnectionError e) {
//            if (!mute && !essential) System.out.println("Connection error from "+ message.toString());
//            this.gracefulDisconnection(true);
//            throw new ConnectionError();
//        } catch (InterruptedException e) {
//            if (!mute && !essential) System.out.println("Interrupted Exception from "+ message.toString());
//            this.gracefulDisconnection(true);
//            throw new ConnectionError();
//        }




    }


    /**
     * This method retrieves a message from a lock
     * @param lock: the lock
     * @return the message
     * @throws ConnectionError: if the connection fails
     */
    private Message retrieveMessageFromLock(Lock lock) throws ConnectionError {
        synchronized (lock) {
            Message newMessage = lock.getMessage();
            if (lock.isOffline()) throw new ConnectionError();
            if (newMessage == null) {
                if (!mute) System.out.println("Failed to receive response");
                throw new ConnectionError();
            }
            lock.reset();
            return newMessage;
        }

    }

    /**
     * This method notifies the lock that a message has arrived and memorizes the message in the lock
     * @param lock: the lock
     * @param message: the message
     */
    private void notifyLockAndSetMessage(Lock lock, Message message){
        synchronized (lock){
            lock.setMessage(message);
            lock.setToWait(false);
            lock.notify();
        }
    }


    /**
     * This method sends a message over the socket
     * @param message: the message to send
     */
    private void sendTcpMessage(Message message){
        //if (!message.toString().equals("PingClientMessage"))
            if (!mute && !essential) System.out.println("Sending "+message.toString() +" to Server socket");
        try {
            this.objectOutputStream.writeObject(message);
            this.objectOutputStream.flush();
            //this.objectOutputStream.reset();
        } catch (IOException e) {
            if (!mute && !essential) System.out.println("An error occurred while trying to send a message to the server");
            this.gracefulDisconnection(true);
        }
    }


    /**
     * This method manages the reception of a message
     * @param message: the message
     */
    private void manageInboundTcpMessages(Message message){
        //if (!message.toString().equals("PingClientResponse"))
            if (!mute && !essential) System.out.println("Received a "+message.toString()+" from "+message.sender());
        // synchronous messages
        if (message instanceof ChooseNicknameResponse)
            notifyLockAndSetMessage(actionLock, message);
        else if (message instanceof CreateGameResponse)
            notifyLockAndSetMessage(actionLock, message);
        else if (message instanceof RecoverGameResponse)
            notifyLockAndSetMessage(actionLock, message);
        else if (message instanceof JoinGameResponse)
            notifyLockAndSetMessage(actionLock, message);
        else if (message instanceof MakeMoveResponse)
            notifyLockAndSetMessage(actionLock, message);
        else if (message instanceof GetLobbiesResponse) {
            notifyLockAndSetMessage(actionLock, message);
        }
        // The client keeps the heartbeat the server only responds
        else if (message instanceof PingClientResponse);
            // do nothing

        // asynchronous messages
        else if (message instanceof ChatReceiveMessage){
            ChatReceiveMessage m = (ChatReceiveMessage) message;
            this.receiveMessage(m.getChatMessage());
        }
        else if (message instanceof UpdateMessage) {
            UpdateMessage m = (UpdateMessage) message;
            this.update(m.getNewState(), m.getNewInfo());
        }
    }


    // Synchronous methods

    /**
     * This method lets the player choose his nickname
     * @param nick: the nickname of the player
     * @return true if nickname is available
     * @throws ConnectionError if the connection fails
     */
    public synchronized boolean chooseNickname(String nick) throws ConnectionError {
        ChooseNicknameResponse response = (ChooseNicknameResponse) this.manageTcpConversation(actionLock,
                new ChooseNicknameMessage(this.nickname, nick));

        if (response.getResponse()) this.nickname = nick;
        return response.getResponse();
    }

    /**
     * This method lets the player make a move
     * @param pos : a List of positions
     * @param col : the column of the shelf
     * @throws InvalidNicknameException if the nickname is invalid
     * @throws InvalidMoveException if the move is invalid
     * @throws ConnectionError if the connection fails
     * @throws GameEndedException if the game has ended
     */
    public synchronized void makeMove(List<Position> pos, int col) throws InvalidMoveException, InvalidNicknameException, ConnectionError, GameEndedException {
        MakeMoveResponse response = (MakeMoveResponse) this.manageTcpConversation(actionLock,
                new MakeMoveMessage(this.nickname, pos, col));


        if (response.isGameEnded()) throw new GameEndedException();
        if (response.isInvalidMove()) throw new InvalidMoveException();
        if (response.isInvalidNickname()) throw new InvalidNicknameException();
    }

    /**
     * This method lets a player create a game and choose the available player slots
     * @param num : player slots
     * @throws NonExistentNicknameException if the nickname is invalid
     * @throws AlreadyInGameException if the player is already in a game
     * @throws ConnectionError if the connection fails
     */
    public synchronized void createGame(int num) throws NonExistentNicknameException, AlreadyInGameException, ConnectionError {
        CreateGameResponse response = (CreateGameResponse) this.manageTcpConversation(actionLock,
                new CreateGameMessage(this.nickname, num));

        if (response.isNonExistentNickname()) throw new NonExistentNicknameException();
        if (response.isAlreadyInGame()) throw new AlreadyInGameException();
    }

    /**
     * This method lets a player join a game
     * @throws NoGamesAvailableException if there are no games available
     * @throws NonExistentNicknameException if the nickname is invalid
     * @throws AlreadyInGameException if the player is already in a game
     * @throws NoGameToRecoverException if there is no game to recover
     * @throws ConnectionError if the connection fails
     */
    public synchronized void joinGame(String lobbyName) throws NoGamesAvailableException, NonExistentNicknameException, NoGameToRecoverException, AlreadyInGameException, ConnectionError, WrongLobbyIndexException, LobbyFullException {
        JoinGameResponse response = (JoinGameResponse) this.manageTcpConversation(actionLock,
                new JoinGameMessage(this.nickname, lobbyName));

        if (response.isAlreadyInGame()) throw new AlreadyInGameException();
        if (response.isNoGamesAvailable()) throw new NoGamesAvailableException();
        if (response.isNonExistentNickname()) throw new NonExistentNicknameException();
        if (response.isNoGameToRecover()) throw new NoGameToRecoverException();
        if (response.isWrongLobbyIndex()) throw new WrongLobbyIndexException();
        if (response.isLobbyFull()) throw new LobbyFullException();
    }

    /**
     * This method lets a player recover a game from persistence
     * @throws NoGameToRecoverException if there is no game to recover
     * @throws ConnectionError if the connection fails
     */
    public synchronized void recoverGame() throws NoGameToRecoverException, ConnectionError {
        RecoverGameResponse response = (RecoverGameResponse) this.manageTcpConversation(actionLock,
                new RecoverGameMessage(this.nickname));

        if (response.isNoGameToRecover()) throw new NoGameToRecoverException();
    }

    /**
     * This method lets the client send a message privately to someone
     * @param chatMessage: the message
     * @param receiver : the one that is supposed to receive the message
     * @throws ConnectionError if the connection fails
     */
    public synchronized void messageSomeone(String chatMessage, String receiver) throws ConnectionError {
        this.sendTcpMessage(new ChatSomeoneMessage(this.nickname, chatMessage, receiver));
    }

    /**
     * This method lets the client send a message to every other client connected to the game
     * @param chatMessage: the message
     * @throws ConnectionError if the connection fails
     */
    public synchronized void messageAll(String chatMessage) throws ConnectionError {
        this.sendTcpMessage(new ChatAllMessage(this.nickname, chatMessage));
    }

    /**
     * This method retrieve the active lobbies on the server
     *
     * @return the list of the active lobbies
     * @throws ConnectionError if the connection fails
     * @throws NoGamesAvailableException if there are no games available
     */
    @Override
    public List<Lobby> getLobbies() throws ConnectionError, NoGamesAvailableException {
        GetLobbiesResponse response = (GetLobbiesResponse) this.manageTcpConversation(actionLock,
                new GetLobbiesMessage(this.nickname));

        if (response.isNoGamesAvailableException()) throw new NoGamesAvailableException();

        return response.getLobbyList();
    }


    // asynchronous methods

    /**
     * This method updates the view with new information
     * @param newState : the new state of the game
     * @param newInfo : the new info for the view
     */
    private void update(State newState, GameInfo newInfo){
        if (newState == State.GRACEFULDISCONNECTION) this.gracefulDisconnection(true);
        else if (newState == State.GAMEABORTED) this.gracefulDisconnection(false);
        else this.view.update(newState, newInfo);
    }

    /**
     * This method notifies the view that a chat message has arrived
     * @param message: the message
     */
    private void receiveMessage(String message){
        this.view.displayChatMessage(message);
    }

    /**
     * This method manages the disconnection by setting the flags toPing, listeningForMessages, isClientOnline to false,
     * closing the socket and updating the view
     * @param connectionError: boolean that indicates if an error occurred
     */
    private void gracefulDisconnection(boolean connectionError){
        if (isClientOnline) {
            this.isClientOnline = false;
            if (connectionError && !mute && !essential) System.out.println("Connection error");
            else if (!connectionError && !mute) System.out.println("Game Aborted");
            if (!mute) System.out.println("Initializing graceful disconnection");
            if (!mute && !essential) System.out.println("Terminating Ping thread");
            this.toPing = false;
            if (!mute && !essential) System.out.println("Terminating messageListener");
            this.listeningForMessages = false;

            try {
                if (!mute && !essential) System.out.println("Closing socket");
                this.socket.close();
            } catch (IOException e) {
                if (!mute && !essential) System.out.println("Error while closing socket");
            }

            // Notifying lock to stop
            this.actionLock.setOffline(true);

            // Updating the view
            view.update(State.GRACEFULDISCONNECTION, null);

        }
    }
}




