package it.polimi.ingsw.view;

import it.polimi.ingsw.network.client.RmiClient;
import it.polimi.ingsw.controller.exceptions.InvalidMoveException;
import it.polimi.ingsw.controller.exceptions.InvalidNicknameException;
import it.polimi.ingsw.gameInfo.PlayerInfo;
import it.polimi.ingsw.gameInfo.State;
import it.polimi.ingsw.model.Position;
import it.polimi.ingsw.model.SingleGoal;
import it.polimi.ingsw.model.Tile;
import it.polimi.ingsw.model.TileColor;
import it.polimi.ingsw.model.constants.AppConstants;
import it.polimi.ingsw.network.client.TcpClient;
import it.polimi.ingsw.network.client.exceptions.ConnectionError;
import it.polimi.ingsw.network.server.constants.ServerConstants;
import it.polimi.ingsw.network.server.exceptions.AlreadyInGameException;
import it.polimi.ingsw.network.server.exceptions.NoGamesAvailableException;
import it.polimi.ingsw.network.server.exceptions.NonExistentNicknameException;

import java.io.*;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
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
    //TODO: implement this method
    @Override
    protected void display() {
        synchronized (displayLock) {
            // if gameInfo is null the game has not started yet, so we don't need to print nothing
            if (this.gameInfo == null) {
                return;
            }

            System.out.println();
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
        printMessage("Other players' shelf:", AnsiEscapeCodes.GAME_MESSAGE);
        for (PlayerInfo playerInfo : gameInfo.getPlayerInfosList()) {
            if (!playerInfo.getNickname().equals(this.myNickname)) {
                printMessage(playerInfo.getNickname() + "'s shelf:", AnsiEscapeCodes.GAME_MESSAGE);
                printBoardOrShelf(AppConstants.ROWS_NUMBER, AppConstants.COLS_NUMBER, playerInfo.getShelf(), null);
                printMessage("   Common Goal Points: " + playerInfo.getComGoalPoints()[0] + "," + playerInfo.getComGoalPoints()[1], AnsiEscapeCodes.GAME_MESSAGE);
            }
        }
    }

    /**
     * This method is called by display to print the board
     */
    private void printBoard() {
        printMessage("Board:", AnsiEscapeCodes.GAME_MESSAGE);
        printBoardOrShelf(AppConstants.BOARD_DIMENSION, AppConstants.BOARD_DIMENSION, gameInfo.getGameBoard(), null);
    }

    /**
     * This method is called by display to print the shelf of the current player
     */
    //TODO: add char to sign personal goal
    private void printMyShelf() {
        for (PlayerInfo playerInfo : gameInfo.getPlayerInfosList()) {
            if (playerInfo.getNickname().equals(this.myNickname)) {
                printMessage("My shelf:", AnsiEscapeCodes.GAME_MESSAGE);
                printBoardOrShelf(AppConstants.ROWS_NUMBER, AppConstants.COLS_NUMBER, playerInfo.getShelf(), playerInfo.getPersonalGoal());
                String toPrint="   Common Goal Points: | ";
                for(Integer i: this.gameInfo.getCommonGoalsStack()){
                    toPrint+= i +" | ";
                }
                printMessage(toPrint, AnsiEscapeCodes.GAME_MESSAGE);
                return;
            }
        }
    }

    /**
     * This method is called to print the board or a shelf
     * @param xMax the number of rows
     * @param yMax the number of columns
     */
    private void printBoardOrShelf(int yMax, int xMax, Tile[][] boardOrShelf, List<SingleGoal> personalGoal) {
        StringBuilder lineBuilder = new StringBuilder();

        lineBuilder.append("   ");
        for (int i = 0; i < xMax; i++) {
            lineBuilder.append(" ").append(i).append(" ");
        }
        System.out.println(lineBuilder);

        String toPrint;
        for (int i = 0; i < yMax; i++) {
            lineBuilder = new StringBuilder();
            lineBuilder.append(" ").append(i).append(" ");
            for (int j = 0; j < xMax; j++) {
                toPrint = "   ";
                if (personalGoal != null) {
                    for (SingleGoal singleGoal : personalGoal) {
                        if (singleGoal.getPosition().equals(new Position(j, i))) {
                            toPrint = tileColorToAnsiCode(singleGoal.getColor(), false) + " X " + AnsiEscapeCodes.ENDING_CODE.getCode();
                            break;
                        }
                    }
                }
                lineBuilder.append(tileColorToAnsiCode(boardOrShelf[i][j].getColor(), true)).append(toPrint).append(AnsiEscapeCodes.ENDING_CODE.getCode());
            }
            lineBuilder.append(" ").append(i).append(" ");
            System.out.println(lineBuilder);
        }

        lineBuilder = new StringBuilder();
        lineBuilder.append("   ");
        for (int i = 0; i < xMax; i++) {
            lineBuilder.append(" ").append(i).append(" ");
        }
        System.out.println(lineBuilder);
    }

    /**
     * This method is called by printBoard to convert a tile color to an ANSI escape code
     * @param color the color to convert
     * @return the ANSI escape code
     */
    private String tileColorToAnsiCode(TileColor color, boolean isBackground) {
        return switch (color) {
            case WHITE -> isBackground ? AnsiEscapeCodes.WHITE_BACKGROUND.getCode() : AnsiEscapeCodes.WHITE_TEXT.getCode();
            case BLUE -> isBackground ? AnsiEscapeCodes.BLUE_BACKGROUND.getCode() : AnsiEscapeCodes.BLUE_TEXT.getCode();
            case YELLOW -> isBackground ? AnsiEscapeCodes.YELLOW_BACKGROUND.getCode() : AnsiEscapeCodes.YELLOW_TEXT.getCode();
            case VIOLET -> isBackground ? AnsiEscapeCodes.VIOLET_BACKGROUND.getCode() : AnsiEscapeCodes.VIOLET_TEXT.getCode();
            case CYAN -> isBackground ? AnsiEscapeCodes.CYAN_BACKGROUND.getCode() : AnsiEscapeCodes.CYAN_TEXT.getCode();
            case GREEN -> isBackground ? AnsiEscapeCodes.GREEN_BACKGROUND.getCode() : AnsiEscapeCodes.GREEN_TEXT.getCode();
            case EMPTY -> isBackground ? AnsiEscapeCodes.EMPTY_BACKGROUND.getCode() : AnsiEscapeCodes.EMPTY_TEXT.getCode();
            default -> isBackground ? AnsiEscapeCodes.DEFAULT_BACKGROUND.getCode() : AnsiEscapeCodes.DEFAULT_TEXT.getCode();
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
            default -> printMessage("invalid command, please try again ", AnsiEscapeCodes.ERROR_MESSAGE);
        }
    }

    /**
     * This method is called by parseCommand to parse a move command
     */
    //TODO: change implementation to match changes in parseCommand
    private void parseMoveCommand() {
        List<Position> positions = new ArrayList<>();
        int column;
        synchronized (displayLock) {
            if (!isMyTurn()) {
                printMessage("Error: please wait for your turn ", AnsiEscapeCodes.ERROR_MESSAGE);
                return;
            }

            printMessage("Select a the tiles you want to pick (x1,y1 x2,y2 x3,y3) ", AnsiEscapeCodes.INFO_MESSAGE);
            String input = this.retryInput("^[0-9]+,[0-9]+$|^[0-9]+,[0-9]+ [0-9]+,[0-9]+$|^[0-9]+,[0-9]+ [0-9]+,[0-9]+ [0-9]+,[0-9]+$");

            for (String position : input.split(" ")) {
                positions.add(new Position(Integer.parseInt(position.split(",")[0]), Integer.parseInt(position.split(",")[1])));
            }

            printMessage("Select the column were you want to place the tiles ", AnsiEscapeCodes.INFO_MESSAGE);

            input = this.retryInput("^[0-"+(AppConstants.COLS_NUMBER-1)+"]+$");

            column = Integer.parseInt(input);
        }

            try {
                client.makeMove(positions, column);
                printMessage("Move sent ", AnsiEscapeCodes.INFO_MESSAGE);
            } catch (InvalidNicknameException e) {
                printMessage("Error: invalid nickname ", AnsiEscapeCodes.ERROR_MESSAGE);
            } catch (InvalidMoveException e) {
                printMessage("Error: invalid move please try again ", AnsiEscapeCodes.ERROR_MESSAGE);
            } catch (ConnectionError e) {
                // ignore
            }
    }

    /**
     * This method is called by parseCommand to handle a chat command
     */
    private void chatCommand() {
        synchronized (displayLock) {
            AtomicBoolean messageSent = new AtomicBoolean(false);

            // create and start thread to get input from the user
            Thread getInput = new Thread(() -> {
                printMessage("To send a global message write 'all : message'", AnsiEscapeCodes.INFO_MESSAGE);
                printMessage("To send a message to a specific player write 'player_name : message' ", AnsiEscapeCodes.INFO_MESSAGE);

                while (!messageSent.get()) {
                    try {
                        while (!bufferedReader.ready()) {
                            Thread.sleep(100);
                        }
                        String input = bufferedReader.readLine();
                        while (!input.matches("^[A-Za-z0-9+_.-]+ : (.+)$")) {
                            printMessage("Invalid message format, please try again ", AnsiEscapeCodes.ERROR_MESSAGE);
                            while (!bufferedReader.ready()) {
                                Thread.sleep(100);
                            }
                            input = bufferedReader.readLine();
                        }

                        String receiverNickname = input.substring(0, input.indexOf(":")).trim();
                        String message = input.substring(input.indexOf(":") + 1).trim();
                        if (receiverNickname.equals("all")) {
                            client.messageAll(message);
                            messageSent.set(true);
                        }
                        else {
                            if (checkExistingNickname(receiverNickname)) {
                                client.messageSomeone(message, receiverNickname);
                                messageSent.set(true);
                            }
                            else {
                                printMessage("This player does not exist, please type again your message: ", AnsiEscapeCodes.ERROR_MESSAGE);
                            }
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
                        while (!input.equalsIgnoreCase("y") && !input.equalsIgnoreCase("n")) {
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
        //https://www.geeksforgeeks.org/how-to-validate-an-ip-address-using-regular-expressions-in-java/
        String zeroTo255 ="(\\d{1,2}|(0|1)\\d{2}|2[0-4]\\d|25[0-5])";
        String regexIP="|localhost|"+zeroTo255 + "\\." + zeroTo255 + "\\." + zeroTo255 + "\\." + zeroTo255;
        String ip=this.retryInput(regexIP);
        if(ip.equals("")) ip="localhost";

        printMessage("Insert port of the server (or <default> for automatic detection)", AnsiEscapeCodes.INFO_MESSAGE);
        String port=this.retryInput("|default|\\d+");
        Integer intPort= ServerConstants.RMI_PORT;


        printMessage("Choose connection type (rmi/tcp)", AnsiEscapeCodes.INFO_MESSAGE);
        String input = this.retryInput("rmi|RMI|tcp|TCP");

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
            //ignore
        }
    }

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
                printMessage("Please insert the number of players ", AnsiEscapeCodes.INFO_MESSAGE);
                String playersNumber =this.retryInput("[2-4]");

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
        String input = this.retryInput("y|n");
        return input.equals("y");
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
