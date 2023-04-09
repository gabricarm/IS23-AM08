package it.polimi.ingsw.server;

import it.polimi.ingsw.client.RmiClient;
import it.polimi.ingsw.client.RmiClientInterface;
import it.polimi.ingsw.controller.exceptions.InvalidIdException;
import it.polimi.ingsw.controller.exceptions.InvalidMoveException;
import it.polimi.ingsw.model.Position;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

public interface RmiServerInterface extends Remote {
    public boolean makeMove(List<Position> pos, int col, String nickname) throws RemoteException, InvalidIdException, InvalidMoveException;

    // This exists only for debugging purposes
    public void registerPlayer(String nickname, RmiClientInterface client) throws RemoteException;
}