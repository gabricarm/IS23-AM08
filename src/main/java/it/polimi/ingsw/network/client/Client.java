package it.polimi.ingsw.network.client;

import it.polimi.ingsw.controller.exceptions.InvalidNicknameException;
import it.polimi.ingsw.controller.exceptions.InvalidMoveException;
import it.polimi.ingsw.model.Position;
import it.polimi.ingsw.network.client.exceptions.ConnectionError;
import it.polimi.ingsw.network.client.exceptions.GameEndedException;
import it.polimi.ingsw.network.server.exceptions.AlreadyInGameException;
import it.polimi.ingsw.network.server.exceptions.NonExistentNicknameException;
import it.polimi.ingsw.network.server.exceptions.NoGamesAvailableException;

import java.util.List;

/**
 * This is the client interface, it makes the communication protocol transparent
 * to the rest of the program
 */
public interface Client  {

    /**
     * This method has to be overridden in its subclasses, it asks the server a
     * possible nickname
     * @param nick
     * @return true if successful
     * @throws ConnectionError
     */
    public boolean chooseNickname(String nick) throws ConnectionError;

    /**
     * This method has to be overridden in its subclasses, it tries to make a move
     * @param pos: list of tiles positions
     * @param col: column
     * @throws InvalidNicknameException
     * @throws InvalidMoveException
     * @throws InvalidNicknameException
     * @throws ConnectionError
     * @throws GameEndedException
     */
    public void makeMove(List<Position> pos, int col) throws InvalidMoveException, InvalidNicknameException, ConnectionError, GameEndedException;

    /**
     * This method has to be overridden in its subclasses, it tries to create a new game
     * @param num: number of players
     * @throws NonExistentNicknameException
     * @throws AlreadyInGameException
     * @throws ConnectionError
     */
    public void createGame(int num) throws NonExistentNicknameException, AlreadyInGameException,ConnectionError;

    /**
     * This method has to be overridden in its subclasses, it tries to join a new game
     * @throws NoGamesAvailableException
     * @throws NonExistentNicknameException
     * @throws AlreadyInGameException
     * @throws ConnectionError
     */
    public void joinGame() throws NoGamesAvailableException, NonExistentNicknameException, AlreadyInGameException, ConnectionError;

    /**
     * This method has to be overridden in its subclasses, it tries to send a private message
     * @param message: the message
     * @param receiver: the one supposed to receive the message
     * @throws ConnectionError
     */
    public void messageSomeone(String message, String receiver) throws ConnectionError;

    /**
     * This method has to be overridden in its subclasses, it tries to send a public message
     * @param message: the message
     * @throws ConnectionError
     */
    public void messageAll(String message) throws ConnectionError;

}
