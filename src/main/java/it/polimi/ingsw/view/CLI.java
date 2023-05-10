package it.polimi.ingsw.view;

import it.polimi.ingsw.constants.ViewConstants;
import it.polimi.ingsw.constants.ModelConstants;
import it.polimi.ingsw.network.client.RmiClient;
import it.polimi.ingsw.controller.exceptions.InvalidMoveException;
import it.polimi.ingsw.controller.exceptions.InvalidNicknameException;
import it.polimi.ingsw.gameInfo.PlayerInfo;
import it.polimi.ingsw.gameInfo.State;
import it.polimi.ingsw.model.Position;
import it.polimi.ingsw.model.SingleGoal;
import it.polimi.ingsw.model.Tile;
import it.polimi.ingsw.model.TileColor;
import it.polimi.ingsw.network.client.TcpClient;
import it.polimi.ingsw.network.client.exceptions.ConnectionError;
import it.polimi.ingsw.network.client.exceptions.GameEndedException;
import it.polimi.ingsw.constants.ServerConstants;
import it.polimi.ingsw.network.server.exceptions.AlreadyInGameException;
import it.polimi.ingsw.network.server.exceptions.NoGamesAvailableException;
import it.polimi.ingsw.network.server.exceptions.NonExistentNicknameException;

import java.io.*;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * This class is used to represent the CLI view of the game
 */
public class CLI extends View{

    /**
     * This attribute is used to synchronize the visual update of the view
     */
    private final Object displayLock = new Object();

    /**
     * This attribute is used to set a timeout for the user input to avoid deadlocks
     */
    private static ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    /**
     * Scanner used to read user input
     */
    private static Scanner scanner = new Scanner(System.in);

    /**
     * BufferedReader used to read user input
     */
    private static final BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(System.in));


    /**
     * This method is called by getUserInput to wait for other players to join the game
     */
    @Override
    protected void waitForGameStart() {
        printMessage("Waiting for other players to join the game...", AnsiEscapeCodes.INFO_MESSAGE);
        while (this.currentState == State.WAITINGFORPLAYERS) {
            try {
                wait(1000);
            } catch (InterruptedException ignored) {

            }
        }
    }

    /**
     * This method is called by update to display the game
     */
    @Override
    protected void display() {
        synchronized (displayLock) {
            // if gameInfo is null the game has not started yet, so we don't need to print nothing
            if (this.gameInfo == null) {
                return;
            }

            System.out.println();

            if (currentState == State.ENDGAME) {
                printMessage("Game ended!", AnsiEscapeCodes.INFO_MESSAGE);
                printMessage("Final scores:", AnsiEscapeCodes.INFO_MESSAGE);
                for (PlayerInfo playerInfo : gameInfo.getPlayerInfosList()) {
                    printMessage(playerInfo.getNickname() + ": " + playerInfo.getScore(), AnsiEscapeCodes.INFO_MESSAGE);
                }
                return;
            }

            printOtherPlayersShelf();
            printBoard();
            printCommonGoals();
            printMyShelf();

            if (isMyTurn()) {
                printMessage("It's your turn!", AnsiEscapeCodes.INFO_MESSAGE);
            }
        }
    }

    /**
     * This method is called by display to print the shelf of the other players
     */
    private void printOtherPlayersShelf() {
        System.out.println();
        StringBuilder names = new StringBuilder();
        List<StringBuilder> shelves = new ArrayList<>();
        for (int i = 0; i < ViewConstants.SHELF_REPRESENTATION_DIMENSION; i++) {
            shelves.add(new StringBuilder());
        }
        StringBuilder commonGoalPoints = new StringBuilder();

        printMessage("Other players' shelf:", AnsiEscapeCodes.GAME_MESSAGE);

        for (PlayerInfo playerInfo : gameInfo.getPlayerInfosList()) {
            if (!playerInfo.getNickname().equals(this.myNickname)) {
                names.append(playerInfo.getNickname());
                names.append(" ".repeat(Math.max(0, ViewConstants.MAX_NICKNAME_LENGTH - playerInfo.getNickname().length())));

                //printBoardOrShelf(ModelConstants.ROWS_NUMBER, ModelConstants.COLS_NUMBER, playerInfo.getShelf(), null);
                for (int i = 0; i < ViewConstants.SHELF_REPRESENTATION_DIMENSION; i++) {
                    shelves.get(i).append(createBoardOrShelf(ModelConstants.ROWS_NUMBER, ModelConstants.COLS_NUMBER, playerInfo.getShelf(), null).get(i))
                            .append("   ");
                }

                String commonGoalPointsString = "   Common Goal Points: " + playerInfo.getComGoalPoints()[0] + "," + playerInfo.getComGoalPoints()[1];
                commonGoalPoints.append(commonGoalPointsString).append(" ".repeat(Math.max(0, ViewConstants.MAX_NICKNAME_LENGTH - commonGoalPointsString.length())));
            }
        }

        System.out.println(names);
        for (StringBuilder line: shelves) {
            System.out.println(line);
        }
        System.out.println(commonGoalPoints);
    }

    /**
     * This method is called by display to print the board
     */
    private void printBoard() {
        System.out.println();
        printMessage("Board:", AnsiEscapeCodes.GAME_MESSAGE);

        //printBoardOrShelf(ModelConstants.BOARD_DIMENSION, ModelConstants.BOARD_DIMENSION, gameInfo.getGameBoard(), null);
        for (StringBuilder line: createBoardOrShelf(ModelConstants.BOARD_DIMENSION, ModelConstants.BOARD_DIMENSION, gameInfo.getGameBoard(), null)) {
            System.out.println(line);
        }
    }

    /**
     * This method is called by display to print the shelf of the current player
     */
    private void printMyShelf() {
        System.out.println();
        for (PlayerInfo playerInfo : gameInfo.getPlayerInfosList()) {
            if (playerInfo.getNickname().equals(this.myNickname)) {
                printMessage("My shelf:", AnsiEscapeCodes.GAME_MESSAGE);

                //printBoardOrShelf(ModelConstants.ROWS_NUMBER, ModelConstants.COLS_NUMBER, playerInfo.getShelf(), playerInfo.getPersonalGoal());
                for (StringBuilder line: createBoardOrShelf(ModelConstants.ROWS_NUMBER, ModelConstants.COLS_NUMBER, playerInfo.getShelf(), playerInfo.getPersonalGoal())) {
                    System.out.println(line);
                }

                StringBuilder toPrint= new StringBuilder("   Common Goal Points: | ");
                for(Integer i: playerInfo.getComGoalPoints()){
                    toPrint.append(i).append(" | ");
                }
                printMessage(toPrint.toString(), AnsiEscapeCodes.GAME_MESSAGE);
                return;
            }
        }
    }

    /**
     * This method create the strings that represent the board or the shelf to be printed
     * @param yMax the number of rows
     * @param xMax the number of columns
     * @param boardOrShelf the board or the shelf to be printed
     * @param personalGoal the personal goal of the player (might be null, in that case the board is printed)
     * @return the strings that represent the board or the shelf to be printed
     */
    private List<StringBuilder> createBoardOrShelf (int yMax, int xMax, Tile[][] boardOrShelf, List<SingleGoal> personalGoal) {
        List<StringBuilder> result = new ArrayList<>();

        result.addAll(createHeaderOrFooter(xMax, true));

        StringBuilder lineBuilder;
        String toPrint;
        for (int i = 0; i < yMax; i++) {
            lineBuilder = new StringBuilder();
            lineBuilder.append(" ").append(i).append(" ");
            lineBuilder.append(xMax == ModelConstants.COLS_NUMBER ? AnsiEscapeCodes.SHELF_BACKGROUND.getCode() : AnsiEscapeCodes.BOARD_BORDER_BACKGROUND.getCode())
                    .append(" ").append(AnsiEscapeCodes.ENDING_CODE.getCode());
            for (int j = 0; j < xMax; j++) {
                toPrint = "   ";
                if (personalGoal != null) {
                    for (SingleGoal singleGoal : personalGoal) {
                        if (singleGoal.getPosition().equals(new Position(j, i))) {
                            toPrint = tileColorToAnsiCode(singleGoal.getColor(), false, i, j)
                                    + (boardOrShelf[i][j].isEmpty() ? " ● " : boardOrShelf[i][j].getColor().equals(singleGoal.getColor()) ? " ● " : " X ")
                                    + AnsiEscapeCodes.ENDING_CODE.getCode();
                            break;
                        }
                    }
                }
                lineBuilder.append(tileColorToAnsiCode(boardOrShelf[i][j].getColor(), true, i, j)).append(toPrint).append(AnsiEscapeCodes.ENDING_CODE.getCode());
                if (xMax == ModelConstants.COLS_NUMBER) {
                    lineBuilder.append(AnsiEscapeCodes.SHELF_BACKGROUND.getCode())
                            .append(" ").append(AnsiEscapeCodes.ENDING_CODE.getCode());
                }
            }
            if (xMax != ModelConstants.COLS_NUMBER) {
                lineBuilder.append(AnsiEscapeCodes.BOARD_BORDER_BACKGROUND.getCode())
                        .append(" ").append(AnsiEscapeCodes.ENDING_CODE.getCode());
            }
            lineBuilder.append(" ").append(i).append(" ");
            result.add(lineBuilder);

            if (i != yMax - 1 && xMax == ModelConstants.COLS_NUMBER) {
                lineBuilder = new StringBuilder();
                result.add(lineBuilder.append("   ").append(AnsiEscapeCodes.SHELF_BACKGROUND.getCode())
                        .append(" ".repeat(Math.max(0, (xMax + 1) * 4 + 1 - 4)))
                        .append(AnsiEscapeCodes.ENDING_CODE.getCode())
                        .append("   "));
            }
        }

        result.addAll(createHeaderOrFooter(xMax, false));

        return result;
    }

    /**
     * This method is called to create the header or the footer of the board or a shelf
     * @param xMax the number of rows
     * @param isHeader true if the header is to be created, false if the footer is to be created
     * @return a list of StringBuilders containing the header or the footer
     */
    private List<StringBuilder> createHeaderOrFooter(int xMax, boolean isHeader) {
        List<StringBuilder> result = new ArrayList<>();
        if (isHeader) {
            result.add(createIndexes(xMax));
            result.add(createFirstOrLastRow(xMax));
        }
        else {
            result.add(createFirstOrLastRow(xMax));
            result.add(createIndexes(xMax));
        }

        return result;
    }

    /**
     * This method is called to create the first or last row of the board or a shelf
     * @param xMax the number of rows
     * @return a StringBuilder containing the first or last row
     */
    private StringBuilder createFirstOrLastRow(int xMax) {
        StringBuilder result = new StringBuilder();

        result.append("   ").append(xMax == ModelConstants.COLS_NUMBER ? AnsiEscapeCodes.SHELF_BACKGROUND.getCode() : AnsiEscapeCodes.BOARD_BORDER_BACKGROUND.getCode())
                .append(" ").append(AnsiEscapeCodes.ENDING_CODE.getCode());
        for (int i = 0; i < xMax; i++) {
            result.append(xMax == ModelConstants.COLS_NUMBER ? AnsiEscapeCodes.SHELF_BACKGROUND.getCode() : AnsiEscapeCodes.BOARD_BORDER_BACKGROUND.getCode())
                    .append(xMax == ModelConstants.COLS_NUMBER ? "    " : "   ").append(AnsiEscapeCodes.ENDING_CODE.getCode());
        }

        if (xMax != ModelConstants.COLS_NUMBER) result.append(AnsiEscapeCodes.BOARD_BORDER_BACKGROUND.getCode()).append(" ").append(AnsiEscapeCodes.ENDING_CODE.getCode());

        result.append("   ");
        return result;
    }

    /**
     * This method is called to create the indexes of the board or a shelf
     * @param xMax the number of rows
     * @return a StringBuilder containing the indexes
     */
    private StringBuilder createIndexes(int xMax) {
        StringBuilder result = new StringBuilder();

        result.append("    ");
        for (int i = 0; i < xMax; i++) {
            if (xMax != ModelConstants.COLS_NUMBER)
                result.append(" ").append(i).append(" ");
            else
                result.append(" ").append(i).append("  ");
        }
        result.append("   ");
        return result;
    }

    /**
     * This method is called by printBoard to convert a tile color to an ANSI escape code
     * @param color the color to convert
     * @return the ANSI escape code
     */
    private String tileColorToAnsiCode(TileColor color, boolean isBackground, int i, int j) {
        return switch (color) {
            case WHITE -> isBackground ? AnsiEscapeCodes.WHITE_BACKGROUND.getCode() : AnsiEscapeCodes.WHITE_TEXT.getCode();
            case BLUE -> isBackground ? AnsiEscapeCodes.BLUE_BACKGROUND.getCode() : AnsiEscapeCodes.BLUE_TEXT.getCode();
            case YELLOW -> isBackground ? AnsiEscapeCodes.YELLOW_BACKGROUND.getCode() : AnsiEscapeCodes.YELLOW_TEXT.getCode();
            case VIOLET -> isBackground ? AnsiEscapeCodes.VIOLET_BACKGROUND.getCode() : AnsiEscapeCodes.VIOLET_TEXT.getCode();
            case CYAN -> isBackground ? AnsiEscapeCodes.CYAN_BACKGROUND.getCode() : AnsiEscapeCodes.CYAN_TEXT.getCode();
            case GREEN -> isBackground ? AnsiEscapeCodes.GREEN_BACKGROUND.getCode() : AnsiEscapeCodes.GREEN_TEXT.getCode();
            case EMPTY -> isBackground ? AnsiEscapeCodes.EMPTY_BACKGROUND.getCode() : AnsiEscapeCodes.EMPTY_TEXT.getCode();
            default -> isBackground ?
                    (i + j) % 2 == 0 ? AnsiEscapeCodes.DEFAULT_EVEN_BACKGROUND.getCode() : AnsiEscapeCodes.DEFAULT_ODD_BACKGROUND.getCode() :
                    AnsiEscapeCodes.DEFAULT_TEXT.getCode();
        };
    }

    /**
     * This method is called by display to print the common goals
     */
    private void printCommonGoals() {
        printMessage("Common goals:", AnsiEscapeCodes.GAME_MESSAGE);
        for(int i=0;i<this.gameInfo.getCommonGoalsCreated().size();i++){
            printMessage(i+") " + getGoalDescription(this.gameInfo.getCommonGoalsCreated().get(i)), AnsiEscapeCodes.GAME_MESSAGE);
        }
        StringBuilder lineBuilder = new StringBuilder();
        lineBuilder.append("   Common goals points: | ");
        for(Integer i: this.gameInfo.getCommonGoalsStack()){
            lineBuilder.append(i).append(" | ");
        }
        printMessage(lineBuilder.toString(), AnsiEscapeCodes.GAME_MESSAGE);
    }

    /**
     * This method is called by printCommonGoals to get the description of a goal
     * @param goalIndex the index of the goal
     * @return the description of the goal
     */
    public String getGoalDescription(int goalIndex) {
        return switch (goalIndex) {
            case 0 -> "6 groups of 2 tiles";
            case 1 -> "4 groups of 4 tiles";
            case 2 -> "4 corners of the same color";
            case 3 -> "Two squares of 2x2 tiles";
            case 4 -> "3 columns formed by 6 tiles of maximum 3 different colors";
            case 5 -> "8 tiles of the same color";
            case 6 -> "5 tiles of the same color forming a diagonal";
            case 7 -> "4 rows formed by 6 tiles of maximum 3 different colors";
            case 8 -> "2 columns formed by 6 tiles of 6 different colors";
            case 9 -> "2 rows formed by 6 tiles of 6 different colors";
            case 10 -> "5 tiles of the same color forming a cross";
            default -> "Ladder";
        };
    }

    /**
     * This method is called by start to wait for a command from the player
     *
     * @return the command represented as a string
     */
    @Override
    public String waitCommand() {
        System.out.println();
        printMessage("Waiting for command (/help for command list) ", AnsiEscapeCodes.INFO_MESSAGE);

        scanner = new Scanner(System.in);
        return scanner.nextLine();
    }

    /**
     * This method is called by waitCommand to parse the command and call the right method
     * @param command the command to parse
     */
    @Override
    protected void parseCommand(String command) {
        if (command == null) {
            return;
        }

        switch (command.trim()) {
            case "/help" -> {
                printMessage("Command list:", AnsiEscapeCodes.INFO_MESSAGE);
                printMessage("/help: show this list", AnsiEscapeCodes.INFO_MESSAGE);
                printMessage("/move: move a tile", AnsiEscapeCodes.INFO_MESSAGE);
                printMessage("/chat: send a message to the chat", AnsiEscapeCodes.INFO_MESSAGE);
                printMessage("/exit: exit the game", AnsiEscapeCodes.INFO_MESSAGE);
            }
            case "/move" -> parseMoveCommand();
            case "/chat" -> chatCommand();
            case "/exit" -> confirmExit();
            /*
            case "/shrek"-> printMessage("""
                    ⢀⡴⠑⡄⠀⠀⠀⠀⠀⠀⠀⣀⣀⣤⣤⣤⣀⡀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀
                    ⠸⡇⠀⠿⡀⠀⠀⠀⣀⡴⢿⣿⣿⣿⣿⣿⣿⣿⣷⣦⡀⠀⠀⠀⠀⠀⠀⠀⠀⠀
                    ⠀⠀⠀⠀⠑⢄⣠⠾⠁⣀⣄⡈⠙⣿⣿⣿⣿⣿⣿⣿⣿⣆⠀⠀⠀⠀⠀⠀⠀⠀
                    ⠀⠀⠀⠀⢀⡀⠁⠀⠀⠈⠙⠛⠂⠈⣿⣿⣿⣿⣿⠿⡿⢿⣆⠀⠀⠀⠀⠀⠀⠀
                    ⠀⠀⠀⢀⡾⣁⣀⠀⠴⠂⠙⣗⡀⠀⢻⣿⣿⠭⢤⣴⣦⣤⣹⠀⠀⠀⢀⢴⣶⣆
                    ⠀⠀⢀⣾⣿⣿⣿⣷⣮⣽⣾⣿⣥⣴⣿⣿⡿⢂⠔⢚⡿⢿⣿⣦⣴⣾⠁⠸⣼⡿
                    ⠀⢀⡞⠁⠙⠻⠿⠟⠉⠀⠛⢹⣿⣿⣿⣿⣿⣌⢤⣼⣿⣾⣿⡟⠉⠀⠀⠀⠀⠀
                    ⠀⣾⣷⣶⠇⠀⠀⣤⣄⣀⡀⠈⠻⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⡇⠀⠀⠀⠀⠀⠀
                    ⠀⠉⠈⠉⠀⠀⢦⡈⢻⣿⣿⣿⣶⣶⣶⣶⣤⣽⡹⣿⣿⣿⣿⡇⠀⠀⠀⠀⠀⠀
                    ⠀⠀⠀⠀⠀⠀⠀⠉⠲⣽⡻⢿⣿⣿⣿⣿⣿⣿⣷⣜⣿⣿⣿⡇⠀⠀⠀⠀⠀⠀
                    ⠀⠀⠀⠀⠀⠀⠀⠀⢸⣿⣿⣷⣶⣮⣭⣽⣿⣿⣿⣿⣿⣿⣿⠀⠀⠀⠀⠀⠀⠀
                    ⠀⠀⠀⠀⠀⠀⣀⣀⣈⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⠇⠀⠀⠀⠀⠀⠀⠀
                    ⠀⠀⠀⠀⠀⠀⢿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⠃⠀⠀⠀⠀⠀⠀⠀⠀
                    ⠀⠀⠀⠀⠀⠀⠀⠹⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⡿⠟⠁⠀⠀⠀⠀⠀⠀⠀⠀⠀
                    ⠀⠀⠀⠀⠀⠀⠀⠀⠀⠉⠛⠻⠿⠿⠿⠿⠛⠉
                    """, AnsiEscapeCodes.INFO_MESSAGE);
            */
            default -> printMessage("Invalid command, please try again ", AnsiEscapeCodes.ERROR_MESSAGE);
        }
    }

    /**
     * This method is called by parseCommand to parse a move command, also check the move
     */
    //TODO: change implementation to match changes in parseCommand
    private void parseMoveCommand() {
        List<Position> positions = new ArrayList<>();
        String input;
        String answer = "y";
        int column;
        synchronized (displayLock) {
            if (!isMyTurn()) {
                printMessage("Error: please wait for your turn ", AnsiEscapeCodes.ERROR_MESSAGE);
                return;
            }

            while (positions.size() < 3 && answer.equalsIgnoreCase("y")) {
                printMessage("Select the tile you want to pick (x,y)", AnsiEscapeCodes.INFO_MESSAGE);
                input = this.retryInput(ViewConstants.REGEX_INPUT_SINGLE_MOVE);
                Position pos = new Position(Integer.parseInt(input.substring(0, 1)), Integer.parseInt(input.substring(2, 3)));
                while (((!checkValidPosition(positions, pos)) || positions.contains(pos))){
                    if (!checkValidPosition(positions, pos)) {
                        printMessage("Invalid position: please select another tile", AnsiEscapeCodes.ERROR_MESSAGE);
                        input = this.retryInput(ViewConstants.REGEX_INPUT_SINGLE_MOVE);
                        pos = new Position(Integer.parseInt(input.substring(0, 1)), Integer.parseInt(input.substring(2, 3)));
                    } else {
                        printMessage("Already chosen: please select another tile", AnsiEscapeCodes.ERROR_MESSAGE);
                        input = this.retryInput(ViewConstants.REGEX_INPUT_SINGLE_MOVE);
                        pos = new Position(Integer.parseInt(input.substring(0, 1)), Integer.parseInt(input.substring(2, 3)));
                    }
                }
                positions.add(pos);
                if (positions.size() < 3) {
                    if(!getAdj(positions).isEmpty()){
                        printMessage("Do you want to select another tile? (y/n)", AnsiEscapeCodes.INFO_MESSAGE);
                        answer = this.retryInput(ViewConstants.REGEX_INPUT_YES_OR_NO);
                    }
                    else{
                        printMessage("You can't select another tiles : no more moves avaliable", AnsiEscapeCodes.INFO_MESSAGE);
                        answer = "n";
                    }
                }
            }


        answer = "y";
        // display the chosen tiles and ask if the player wants to order them
        if (positions.size() > 1) {
            while (!answer.equalsIgnoreCase("n")) {
                displayChosenTiles(positions, gameInfo.getGameBoard());
                printMessage("The tiles will be inserted the way you see them, from left to right. Do you want to change the order? (y/n)", AnsiEscapeCodes.INFO_MESSAGE);
                answer = this.retryInput(ViewConstants.REGEX_INPUT_YES_OR_NO);
                if (answer.equals("y")) {
                    printMessage("Select the order of the tiles " + (positions.size() == 2 ? "(1,2)" : "(1,2,3)"), AnsiEscapeCodes.INFO_MESSAGE);
                    input = this.retryInput(positions.size() == 2 ? ViewConstants.REGEX_INPUT_ORDER_2TILES : ViewConstants.REGEX_INPUT_ORDER_3TILES);
                    positions = changeOrder(positions, input);
                    displayChosenTiles(positions, gameInfo.getGameBoard());
                    printMessage("Do you want to change the order again? (y/n)", AnsiEscapeCodes.INFO_MESSAGE);
                    answer = this.retryInput(ViewConstants.REGEX_INPUT_YES_OR_NO);
                }
            }
        }

            //for (String position : input.split(" ")) {
            //  positions.add(new Position(Integer.parseInt(position.split(",")[0]), Integer.parseInt(position.split(",")[1])));
            //}


            printMessage("Select the column where you want to place the tiles ", AnsiEscapeCodes.INFO_MESSAGE);
            input = this.retryInput(ViewConstants.REGEX_INPUT_COLUMN);
            column = Integer.parseInt(input);
            while (!checkColumn(column, positions.size())) {
                printMessage("Incorrect column: please select another column", AnsiEscapeCodes.ERROR_MESSAGE);
                input = this.retryInput(ViewConstants.REGEX_INPUT_COLUMN);
                column = Integer.parseInt(input);
            }
        }

            try {
                client.makeMove(positions, column);
                printMessage("Move sent ", AnsiEscapeCodes.INFO_MESSAGE);
            } catch (InvalidNicknameException e) {
                printMessage("Error: invalid nickname ", AnsiEscapeCodes.ERROR_MESSAGE);
            } catch (InvalidMoveException e) {
                printMessage("Invalid move: please try again ", AnsiEscapeCodes.ERROR_MESSAGE);
            } catch (ConnectionError e) {
                // ignore
            } catch (GameEndedException e) {
                // this one needs to be managed better
                printMessage("Error: game has already ended", AnsiEscapeCodes.ERROR_MESSAGE);
            }
    }

    /**
     * This method is called by parseMoveCommand to order the selected tiles
     * @param selectedTiles the tiles selected by the user
     * @param input the order chosen by the user
     * @return the ordered list of tiles
     */
    public List<Position> changeOrder(List<Position> selectedTiles, String input){
        List<Position> newOrder = new ArrayList<>();
        for (String positionIndex : input.split(",")) {
            newOrder.add(selectedTiles.get(Integer.parseInt(positionIndex)-1));
        }
        return newOrder;
    }

    /**
     * This method is called by parseMoveCommand to show to the user the selected tiles
     * @param chosenTiles the tiles selected by the user
     */
    private void displayChosenTiles(List<Position> chosenTiles, Tile[][] board) {
        printMessage("You have selected the following tiles: ", AnsiEscapeCodes.INFO_MESSAGE);
        StringBuilder stringBuilder = new StringBuilder();
        int i = 1;
        for (Position position : chosenTiles) {
            stringBuilder.append(tileColorToAnsiCode(board[position.y()][position.x()].getColor(), true, 0, 0))
                    .append(" ").append(i).append(" ");
            stringBuilder.append(AnsiEscapeCodes.DEFAULT_ODD_BACKGROUND.getCode()).append(" ");
            i++;
        }
        stringBuilder.append(AnsiEscapeCodes.ENDING_CODE.getCode());
        System.out.println(stringBuilder);
    }

    /**
     * This method is called by parseCommand to handle a chat command
     */
    private void chatCommand() {
        synchronized (displayLock) {
//            AtomicBoolean messageSent = new AtomicBoolean(false);

            // create and start thread to get input from the user
//            Thread getInput = new Thread(() -> {
                printMessage("To send a global message write 'all: message'", AnsiEscapeCodes.INFO_MESSAGE);
                printMessage("To send a message to a specific player write 'player_name: message' ", AnsiEscapeCodes.INFO_MESSAGE);

//                while (!messageSent.get()) {
                    try {
//                        while (!bufferedReader.ready()) {
//                            Thread.sleep(100);
//                        }
//                        String input = bufferedReader.readLine();
//                        while (!input.matches(ViewConstants.REGEX_INPUT_CHAT_MESSAGE)) {
//                            printMessage("Invalid message format, please try again ", AnsiEscapeCodes.ERROR_MESSAGE);
//                            while (!bufferedReader.ready()) {
//                                Thread.sleep(100);
//                            }
//                            input = bufferedReader.readLine();
//                        }
                        String input = this.retryInput(ViewConstants.REGEX_INPUT_CHAT_MESSAGE);

                        String receiverNickname = input.substring(0, input.indexOf(":")).trim();
                        String message = input.substring(input.indexOf(":") + 1).trim();
                        if (receiverNickname.equals("all")) {
                            client.messageAll(message);
//                            messageSent.set(true);
                        }
                        else {
                            if (checkExistingNickname(receiverNickname)) {
                                client.messageSomeone(message, receiverNickname);
//                                messageSent.set(true);
                            }
                            else {
                                printMessage("This player does not exist, please type again: ", AnsiEscapeCodes.ERROR_MESSAGE);
                            }
                        }
                    } catch (Exception ignored) {
                        System.out.println("Exception: Kaboom");
                    }
//                }
//            });
//            getInput.start();
//
//            try {
//                getInput.join(ViewConstants.CHAT_TIMER);
//                if(getInput.isAlive()) {
//                    getInput.interrupt();
//                }
//            } catch (InterruptedException ignored) {
//                System.out.println("Interrupted exception from Chat");
//
//            }
        }
    }

    /**
     * This method is called by chatCommand to check if the given nickname exists
     * @param nickname nickname to check
     * @return true if the nickname is present
     */
    private boolean checkExistingNickname(String nickname) {
        return gameInfo.getPlayerInfosList().stream().map(PlayerInfo::getNickname).anyMatch(n->n.equals(nickname));
    }

    /**
     * This method is called by parseCommand to ask the player to confirm she wants to exit
     */
    private void confirmExit() {
        synchronized (displayLock) {

            AtomicBoolean messageSent = new AtomicBoolean(false);

            // create and start thread to get input from the user
            Thread getInput = new Thread(() -> {
                printMessage("Are you sure you want to exit? (y/n) ", AnsiEscapeCodes.INFO_MESSAGE);

                while (!messageSent.get()) {
                    try {
                        while (!bufferedReader.ready()) {
                            Thread.sleep(100);
                        }
                        String input = bufferedReader.readLine();
                        while (!input.matches(ViewConstants.REGEX_INPUT_YES_OR_NO)) {
                            printMessage("Invalid input, please try again", AnsiEscapeCodes.ERROR_MESSAGE);
                            while (!bufferedReader.ready()) {
                                Thread.sleep(100);
                            }
                            input = bufferedReader.readLine();
                            input = input.trim();
                        }

                        if (input.equalsIgnoreCase("y")) {
                            close("Client closing, bye bye!");
                            messageSent.set(true);
                        }
                        else {
                            printMessage("Returning to game ", AnsiEscapeCodes.INFO_MESSAGE);
                            messageSent.set(true);
                        }
                    } catch (Exception ignored) {

                    }
                }
            });
            getInput.start();

            try {
                getInput.join(10000);
                if(getInput.isAlive()) {
                    printMessage("ancora vivo", AnsiEscapeCodes.ERROR_MESSAGE);
                    getInput.interrupt();
                }
                else printMessage("finito", AnsiEscapeCodes.GAME_MESSAGE);
            } catch (InterruptedException ignored) {

            }
        }
    }

    /**
     * This method is called by parseCommand to print an error message if the command is invalid
     */
    public void printMessage(String message, AnsiEscapeCodes message_type) {
        System.out.println(message_type.getCode() + message + AnsiEscapeCodes.ENDING_CODE.getCode());
    }

    /**
     * This method is called by start to ask the player
     * if he wants to connect via rmi or socket
     * and to which port and ip he wants to connect
     */
    @Override
    // ask for connection type using println, 1 for RMI, 2 for Socket, ask again if the input is not valid
    public void chooseConnectionType() {
        printMessage("Insert ip of the server", AnsiEscapeCodes.INFO_MESSAGE);

        String ip=this.retryInput(ViewConstants.REGEX_INPUT_IP);
        if(ip.equals("")) ip="localhost";

        printMessage("Insert port of the server (or <default> for automatic detection)", AnsiEscapeCodes.INFO_MESSAGE);
        String port=this.retryInput(ViewConstants.REGEX_INPUT_PORT);
        Integer intPort= ServerConstants.RMI_PORT;


        printMessage("Choose connection type (rmi/tcp)", AnsiEscapeCodes.INFO_MESSAGE);
        String input = this.retryInput(ViewConstants.REGEX_INPUT_CONNECTION_TYPE);

        if (input.equalsIgnoreCase("rmi")) {
            try {
                if(port.equals("default") || port.equals("")) intPort=ServerConstants.RMI_PORT;
                else intPort=Integer.valueOf(port);
                client = new RmiClient(myNickname, this, ip, intPort);
            } catch (RemoteException | NotBoundException | InterruptedException e) {
                printMessage("error while connecting to the server", AnsiEscapeCodes.ERROR_MESSAGE);
                close("Client closing, try again later");
            }
        }
        else {
            try {
                if (port.equals("default") || port.equals("")) intPort = ServerConstants.TCP_PORT;
                else intPort = Integer.valueOf(port);
                client = new TcpClient(myNickname, this, ip, intPort);
            } catch (InterruptedException e) {
                printMessage("error while connecting to the server", AnsiEscapeCodes.ERROR_MESSAGE);
                close("Client closing, try again later");
            } catch (ConnectionError e) {
                //ignore?
            }
        }
    }

    /**
     * This method is called by start to ask the player his nickname and send it to the server
     */
    @Override
    public void askNickname() {
        try {

            printMessage("Please insert your nickname: ", AnsiEscapeCodes.INFO_MESSAGE);
            myNickname = scanner.nextLine();
            while (myNickname.equals("") || !client.chooseNickname(myNickname)) {
                printMessage("Invalid nickname, please try again ", AnsiEscapeCodes.ERROR_MESSAGE);
                myNickname = scanner.nextLine();
            }
        } catch (ConnectionError e) {
            // ignore
        }
    }

    // delete this line1
    /**
     * This method is called by start to ask the player if he wants to create a new game or join an existing one
     */
    @Override
    public void createOrJoinGame() {
        boolean gameSelected = false;

        printMessage("Do you want to create a new game or join an existing one? (c/j) ", AnsiEscapeCodes.INFO_MESSAGE);

        while (!gameSelected) {
            String input = this.retryInput("c|j");

            if (input.equals("c")) {
                printMessage("Choose the number of players ", AnsiEscapeCodes.INFO_MESSAGE);
                String playersNumber =this.retryInput(ViewConstants.REGEX_INPUT_INTERVAL_OF_PLAYERS);

                try {
                    client.createGame(Integer.parseInt(playersNumber));
                } catch (NonExistentNicknameException | AlreadyInGameException e) {
                    throw new RuntimeException(e);
                } catch (ConnectionError e) {
                    //ignore
                }
                gameSelected = true;
            }
            else {
                try {
                    try {
                        client.joinGame();
                    } catch (NonExistentNicknameException | AlreadyInGameException e) {
                        throw new RuntimeException(e);
                    } catch (ConnectionError e) {
                        //ignore
                    }
                    gameSelected = true;
                } catch (NoGamesAvailableException e) {
                    printMessage("No games available, please create a new one ", AnsiEscapeCodes.ERROR_MESSAGE);
                }
            }
        }
    }

    /**
     * This method is called by start to ask the player if he wants to play again
     *
     * @return true if the player wants to play again, false otherwise
     */
    @Override
    public boolean askIfWantToPlayAgain() {
        printMessage("Do you want to play again? (y/n) ", AnsiEscapeCodes.INFO_MESSAGE);
        String input = this.retryInput(ViewConstants.REGEX_INPUT_YES_OR_NO);
        return input.equalsIgnoreCase("y");
    }

    /**
     * This method is called by close to notify the player that the client is shutting down
     *
     * @param message the message to display
     */
    @Override
    protected void notifyClose(String message) {
        printMessage(message, AnsiEscapeCodes.INFO_MESSAGE);
        try {
            wait(3000);
        } catch (InterruptedException ignored) { }
    }

    /**
     * This method receive a chat message from the server and displays it
     * @param message the message to display
     */
    @Override
    public void displayChatMessage(String message) {
        printMessage(message, AnsiEscapeCodes.CHAT_MESSAGE);
    }

    /**
     * This method acts as a utility for the continous retry of the input insertion based on the match of a regex
     * @param regex regular expression that has to be satisfied
     * @return the correct input based on the regex
     */
    public String retryInput(String regex){
        String input = scanner.nextLine();
        while(!input.trim().matches(regex)){
            printMessage("Invalid input, please try again ", AnsiEscapeCodes.ERROR_MESSAGE);
            input = scanner.nextLine();
        }
        return input.trim();
    }

}
