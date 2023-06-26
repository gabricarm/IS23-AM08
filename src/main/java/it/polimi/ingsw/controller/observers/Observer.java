package it.polimi.ingsw.controller.observers;

import it.polimi.ingsw.model.GameModel;

/**
 * Interface for observer method
 */
public interface Observer {
    public void update(GameModel model);
}
