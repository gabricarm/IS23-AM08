package it.polimi.ingsw.model;

import com.google.gson.annotations.Expose;
import it.polimi.ingsw.model.commonGoals.*;
import it.polimi.ingsw.model.constants.AppConstants;
import it.polimi.ingsw.model.constants.BoardConstants;
import com.google.gson.Gson;
import it.polimi.ingsw.model.exceptions.NoMoreTilesAtStartFillBoardException;
import it.polimi.ingsw.model.exceptions.NoMoreTilesToFillBoardException;
import it.polimi.ingsw.model.utilities.JsonWithExposeSingleton;
import it.polimi.ingsw.model.utilities.RandomSingleton;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * This is the class used for the simulation of the physical game board.
 * It is indeed very articulated, but it can do all the features required for the correct advancement of the game
 */
public final class GameBoard {
    /**
     * Matrix used for the storage of the physical gameboard
     */
    @Expose
    private final Tile[][] myGameBoard;
    /**
     * List of the two common shelfs chosen for the current game
     */
    private final List<CommonGoal> commonGoals;
    /**
     * List of all the 132 tiles that can be found in the game "bucket"
     */
    @Expose
    private final List<Tile> allTiles;

    /**
     * This method is the utility used by the GameModel to get the gameBoard based on the number of players
     * @param numPlayers number of players of the current game (between 2 and 4)
     * @param co
     * @return game board configurated and filled
     */
    public static GameBoard createGameBoard(int numPlayers, List<Integer> co){
        if(numPlayers==2) return new GameBoard(BoardConstants.FILE_CONFIG_GAMEBOARD2, co);
        if(numPlayers==3) return new GameBoard(BoardConstants.FILE_CONFIG_GAMEBOARD3, co);
        return new GameBoard(BoardConstants.FILE_CONFIG_GAMEBOARD4, co);
    }
    /**
     * The constructor creates all the data structures and the utility attributes.
     * Note that the exception should never be thrown since the file will obviously be always present in the directory (unless someone makes a typo)
     * Note also that it creates a temporary object GameBoardConfiguration used to store all the loaded data
     * @param typeOfBoard string chosen in the GameBoardFactory containing the path of the config file
     */
    private GameBoard(String typeOfBoard, List<Integer> cg){
        myGameBoard=new Tile[BoardConstants.BOARD_DIMENSION][BoardConstants.BOARD_DIMENSION];
        commonGoals =new ArrayList<>(BoardConstants.TOTAL_CG_PER_GAME);
        addAllCommonGoals(cg);
        allTiles =new ArrayList<>(BoardConstants.TOTAL_TILES);
        Gson jsonParser= JsonWithExposeSingleton.getJsonWithExposeSingleton();
        Reader fileReader = null;
        try {
            fileReader = new FileReader(typeOfBoard);
        }
        catch(FileNotFoundException e){
            System.out.println(e);
        }
        GameBoardConfiguration g=jsonParser.fromJson(fileReader, GameBoardConfiguration.class);

        fillAllTilesList();
        initialGameBoardFill(g.getValidPositions());
        fillPointStack(g.getPointStack());
    }

    public GameBoard(GameBoard gameBoard, List<Integer> cg){
        this.myGameBoard=gameBoard.myGameBoard;
        this.commonGoals = new ArrayList<>(BoardConstants.TOTAL_CG_PER_GAME);
        addAllCommonGoals(cg);
        this.allTiles=gameBoard.allTiles;
    }

    /**
     * This method gets a list of integers and it creates the common goals based on a mapping done by integer -> objective
     * @param list list of integers
     */
    private void addAllCommonGoals(List<Integer> list){
        for(int i = 0; i< AppConstants.TOTAL_CG_PER_GAME; i++){
            CommonGoal co;
            Integer selected=list.get(i);
            co = switch (selected) {
                case 0 -> new CommonGoal1();
                case 1 -> new CommonGoal2();
                case 2 -> new CommonGoal3();
                case 3 -> new CommonGoal4();
                case 4 -> new CommonGoal5();
                case 5 -> new CommonGoal6();
                case 6 -> new CommonGoal7();
                case 7 -> new CommonGoal8();
                case 8 -> new CommonGoal9();
                case 9 -> new CommonGoal10();
                case 10 -> new CommonGoal11();
                default -> new CommonGoal12();
            };
            this.commonGoals.add(co);
        }
    }

    /**
     * This method fills the bucket of possible tiles with TOTAL_COLORS x TOTAL_TILES_PER_COLOR =132 tiles
     * and a random sprite from 1 to 3
     */
    private void fillAllTilesList(){
        TileColor[] allColors=new TileColor[]{
                TileColor.BLUE,
                TileColor.GREEN,
                TileColor.WHITE,
                TileColor.LIGHT_BLUE,
                TileColor.YELLOW,
                TileColor.VIOLET
        };
        Random r= RandomSingleton.getRandomSingleton();
        for(int j=0; j<BoardConstants.TOTAL_COLORS;j++){
            for(int i = 0; i<BoardConstants.TOTAL_TILES_PER_COLOR; i++){
                allTiles.add(new Tile(allColors[j],r.nextInt(2)+1));
            }
        }
    }

    /**
     * This method is called only in the constructor and its only purpose is to fill the board with the valid and invalid positions
     * The invalid positions are the ones which in the json file are set to 0
     * @param validPositions integer matrix loaded from the json config file containing the information of a valid position
     */
    private void initialGameBoardFill(Integer[][] validPositions){
        Random r= RandomSingleton.getRandomSingleton();
        for(int y=0; y<BoardConstants.BOARD_DIMENSION; y++){
            for(int x=0; x<BoardConstants.BOARD_DIMENSION; x++){
                if(validPositions[y][x]==1) myGameBoard[y][x]=new Tile(allTiles.remove(r.nextInt(allTiles.size())));
                else myGameBoard[y][x]=new Tile(TileColor.INVALID,1);
            }
        }
    }

    /**
     * The method fills the board up until it is possible.
     * It fills just the positions which are empty, so it avoids to overwrite the remaining tiles on the board.
     * If at the start of the method the bag is empty, then it throws an exception that needs to be checked
     * If at some moment after it started filling it finds out that the bag is empty, then also needs to throw an exception (different)
     * that can be checked maybe to avoid calling the function again
     * @throws NoMoreTilesAtStartFillBoardException self-explainatory
     * @throws NoMoreTilesToFillBoardException self-explainatory
     */
    public void fillBoard() throws NoMoreTilesAtStartFillBoardException, NoMoreTilesToFillBoardException {
        if(allTiles.size()==0) throw new NoMoreTilesAtStartFillBoardException();

        Random r= RandomSingleton.getRandomSingleton();
        for(int y=0;y<BoardConstants.BOARD_DIMENSION;y++){
            for(int x=0;x<BoardConstants.BOARD_DIMENSION;x++){
                if(!myGameBoard[y][x].isInvalid() && myGameBoard[y][x].isEmpty()){
                    myGameBoard[y][x]=new Tile(allTiles.remove(r.nextInt(allTiles.size())));
                    if(allTiles.size()==0) throw new NoMoreTilesToFillBoardException();
                }
            }
        }

    }

    /**
     * This method is only called in the constructor and it is used for the creation of the stack for each common shelf
     * @param pointStack integer array loaded from the json config file containing the stack of points(from lowest to highest)
     */
    private void fillPointStack(Integer[] pointStack){
        for(int i = 0; i<BoardConstants.TOTAL_CG_PER_GAME; i++){
            for(int j=0;j<pointStack.length;j++) {
                this.commonGoals.get(i).push(pointStack[i]);
            }
        }
    }
    /**
     * This method checks if the board has to be filled.
     * In detail for each tile in the board(not invaild and not empty) you look if it has any neighbors not empty.
     * If no tile satisfies the condition then it means there are only lonely "islands" on the board and it should be filled again by calling fillBoard()
     * @return true if the board has to be filled
     */
    public boolean hasToBeFilled() {
        for(int y=0;y<BoardConstants.BOARD_DIMENSION;y++){
            for(int x=0;x<BoardConstants.BOARD_DIMENSION;x++){
                if (!myGameBoard[y][x].isInvalid() && !myGameBoard[y][x].isEmpty()) {
                    if (!this.everyAdjactentEmpty(y,x)) return false;
                }
            }
        }
        return true;
    }

    /**
     * This method is useful to the GameModel to check if the user did input a correct move (not empty or invaild)tile
     * @param p coordinates of the tile
     * @return true if the position is effectively occupied by some valid tile
     */
    public boolean positionOccupied(Position p){

        if(!myGameBoard[p.y()][p.x()].isInvalid() && !myGameBoard[p.y()][p.x()].isEmpty()) return true;
        return false;

    }

    /**
     * This method checks if a tile has at least one free adjacent space, useful to the GameModel to check also that it is a valid move
     * @param p coordinates of the tile
     * @return true if the tile in position p has at least one empty space in one of the inal directions
     */
    public boolean hasFreeAdjacent(Position p){

        int x=p.x();
        int y=p.y();
        Tile c;
        if(x>0){
            c=myGameBoard[p.y()][p.x()-1];
            if(!c.isInvalid()) {
                if (c.isEmpty()) return true;
            }
        }
        if(x<BoardConstants.BOARD_DIMENSION-1){
            c=myGameBoard[p.y()][p.x()+1];
            if(!c.isInvalid()) {
                if (c.isEmpty()) return true;
            }
        }
        if(y>0){
            c=myGameBoard[p.y()-1][p.x()];
            if(!c.isInvalid()) {
                if (c.isEmpty()) return true;
            }
        }
        if(y<BoardConstants.BOARD_DIMENSION-1){
            c=myGameBoard[p.y()+1][p.x()];
            if(!c.isInvalid()) {
                if (c.isEmpty()) return true;
            }
        }
        return true;
    }

    /**
     * This method is similar to hasFreeAdjacent, but it checks that every adjacent is empty.
     * Only called in hasToBeFilled()
     * @param y y coordinate of the tile
     * @param x x coordinate of the tile
     * @return true if every adjacent position to the tile is free
     */
    private boolean everyAdjactentEmpty(int y, int x){
        Tile c;
        if(x>0){
            c=myGameBoard[y][x-1];
            if(!c.isInvalid()) {
                if (!c.isEmpty()) return false;
            }
        }
        if(x<BoardConstants.BOARD_DIMENSION-1){
            c=myGameBoard[y][x];
            if(!c.isInvalid()) {
                if (!c.isEmpty()) return false;
            }
        }
        if(y>0){
            c=myGameBoard[y][x];
            if(!c.isInvalid()) {
                if (!c.isEmpty()) return false;
            }
        }
        if(y<BoardConstants.BOARD_DIMENSION-1){
            c=myGameBoard[y][x];
            if(!c.isInvalid()) {
                if (!c.isEmpty()) return false;
            }
        }
        return true;
    }


    public CommonGoal getCommonGoal(int idx){
        return commonGoals.get(idx);
    }


    /**
     * This method is useful to the GameModel when the player makes a move, it removes it from the current board (sets it to empty) and returns it
     * @param p position of the move that needs to be done (assumed correct, since all the controls are done before)
     * @return the tile contained in position p
     */
    public Tile removeTile(Position p){
        Tile copy=new Tile(myGameBoard[p.y()][p.x()]);
        myGameBoard[p.y()][p.x()].setEmpty();
        return copy;
    }
}
