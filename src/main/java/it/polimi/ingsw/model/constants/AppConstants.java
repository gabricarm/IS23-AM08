package it.polimi.ingsw.model.constants;

/**
 * This class is used to store all the constant values needed by the classes of the game model
 */
public class AppConstants {
    /**
     * shelf rows and cols number
     */
    public static int ROWS_NUMBER           =   6;
    public static int COLS_NUMBER           =   5;

    /**
     * board dimension
     */
    public static int BOARD_DIMENSION       =   9;

    /**
     * total personal and common objectives number (is the same for personal and common)
     */
    public static int TOTAL_GOALS =  12;

    /**
     * number of cards color, number of cards per color and total number of cards
     */
    public static int TOTAL_COLORS          =   6;
    public static int TOTAL_TILES_PER_COLOR =  22;
    public static int TOTAL_TILES           = 132;

    /**
     * number of common objectives per game
     */
    public static int TOTAL_CG_PER_GAME =   2;

    /**
     * max number of element in the common objective points stack
     */
    public static int MAX_STACK_CG          =   4;

    /**
     * max number of players per game
     */
    public static int MAX_PLAYERS           =   4;

    /**
     *
     */
    public static int TOTAL_POINTS_FOR_PG  =   6;
    /**
     *
     */
    public static String FILE_CONFIG_PERSONALGOAL = "src/main/config/model/singleObjectives.json";

    public static String PATH_SAVED_FILES = "src/main/resources/savedMatches/";

}
