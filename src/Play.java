import core.Types;
import core.actions.Action;
import core.game.Game;
import core.actors.Tribe;
import players.*;
import players.mc.MonteCarloAgent;
import players.osla.OneStepLookAheadAgent;
import utils.StatSummary;

import java.util.ArrayList;

import static core.Types.GAME_MODE.CAPITALS;
import static core.Types.TRIBE.*;
import static core.Types.TRIBE.OUMAJI;

/**
 * Entry point of the framework.
 */
public class Play {

    enum PlayerType
    {
        HUMAN,
        RANDOM,
        OSLA,
        MC,
        SIMPLE
    }

    public static void main(String[] args) {

        Types.GAME_MODE gameMode = CAPITALS;

        String filename = "levels/SampleLevel2p.csv";
//        String filename = "levels/SampleLevel.csv";
//        String filename = "levels/MinimalLevel.csv";
//        String filename = "levels/MinimalLevel_water.csv";
//        String filename = "levels/MinimalLevel2.csv";

        String saveGameFile = "save/1589357287411/4_0/game.json";


        //THREE WAYS OF RUNNING Tribes:

        //1. Play one game with visuals:
        play(filename, new PlayerType[]{PlayerType.HUMAN, PlayerType.OSLA}, new Types.TRIBE[] {XIN_XI, OUMAJI}, gameMode);

        //2. Play N games without visuals
//        run(filename, new PlayerType[]{PlayerType.OSLA, PlayerType.OSLA}, new Types.TRIBE[] {XIN_XI, OUMAJI}, gameMode, 5);

        //3. Play one game with visuals from a savegame
//        load(new PlayerType[]{PlayerType.OSLA, PlayerType.OSLA}, new Types.TRIBE[] {XIN_XI, OUMAJI}, saveGameFile);

    }

    private static void play(String levelFile, PlayerType[] playerTypes, Types.TRIBE[] tribes, Types.GAME_MODE gameMode)
    {
        KeyController ki = new KeyController(true);
        ActionController ac = new ActionController();


        Game game = _prepareGame(levelFile, playerTypes, tribes, gameMode, ac);
        Run.runGame(game, ki, ac);

        _manageGameResults(game, tribes, null, null);
    }


    private static void load(PlayerType[] playerTypes, Types.TRIBE[] tribes, String saveGameFile)
    {
        KeyController ki = new KeyController(true);
        ActionController ac = new ActionController();

        long randomSeed = System.currentTimeMillis();

        Game game = _loadGame(playerTypes, saveGameFile, randomSeed);
        Run.runGame(game, ki, ac);

        _manageGameResults(game, tribes, null, null);
    }

    private static void run(String levelFile, PlayerType[] playerTypes, Types.TRIBE[] tribes, Types.GAME_MODE gameMode, int repetitions)
    {
        StatSummary[] victories = new StatSummary[playerTypes.length];
        StatSummary[] scores = new StatSummary[playerTypes.length];

        for(int i = 0; i < playerTypes.length; ++i)
        {
            victories[i] = new StatSummary();
            scores[i] = new StatSummary();
        }

        for(int rep = 0; rep < repetitions; rep++)
        {
            Game game = _prepareGame(levelFile, playerTypes, tribes, gameMode, null);
            Run.runGame(game);
            _manageGameResults(game, tribes, victories, scores);
        }
    }

    private static Game _prepareGame(String levelFile, PlayerType[] playerTypes, Types.TRIBE[] tribes, Types.GAME_MODE gameMode, ActionController ac)
    {

        long gameSeed = System.currentTimeMillis();
        long randomSeed = System.currentTimeMillis();
        System.out.println("Game seed: " + gameSeed + ", random seed: " + randomSeed);

        ArrayList<Agent> players = new ArrayList<>();
        ArrayList<Tribe> tribesList = new ArrayList<>();

        for(int i = 0; i < playerTypes.length; ++i)
        {
            Agent ag = _getAgent(playerTypes[i], randomSeed, ac);
            ag.setPlayerID(i);
            players.add(ag);
            tribesList.add(new Tribe(tribes[i]));
        }

        Game game = new Game();
        game.init(players, tribesList, levelFile, gameSeed, gameMode);
        return game;
    }

    private static Game _loadGame(PlayerType[] playerTypes, String saveGameFile, long randomSeed)
    {
        ArrayList<Agent> players = new ArrayList<>();

        for(int i = 0; i < playerTypes.length; ++i)
        {
            Agent ag = _getAgent(playerTypes[i], randomSeed, null);
            ag.setPlayerID(i);
            players.add(ag);
        }

        Game game = new Game();
        game.init(players, saveGameFile);
        return game;
    }

    private static void _manageGameResults(Game game, Types.TRIBE[] tribes, StatSummary[] victories, StatSummary[] scores)
    {
        Types.RESULT[] results = game.getWinnerStatus();
        int[] sc = game.getScores();
        for(int i = 0; i < results.length; ++i)
        {
            System.out.println("Tribe " + i + " (" + tribes[i] + "): " + results[i] + ", " + sc[i] + " points.");
            if(victories != null)
            {
                victories[i].add(results[i] == Types.RESULT.WIN ? 1 : 0);
                scores[i].add(sc[i]);
            }
        }

        if(victories != null)
        {
            for(int i = 0; i < results.length; ++i)
            {
                System.out.println("Tribe " + i + " (" + tribes[i] + "): " + (int) victories[i].sum() + "/" + victories[0].n() + " victories, " +
                        scores[i].mean() + " average points.");
            }
        }

    }

    private static Agent _getAgent(PlayerType playerType, long randomSeed, ActionController ac)
    {
        switch (playerType)
        {
            case HUMAN: return new HumanAgent(ac);
            case RANDOM: return new RandomAgent(randomSeed);
            case OSLA: return new OneStepLookAheadAgent(randomSeed);
            case MC: return new MonteCarloAgent(randomSeed);
            case SIMPLE: return new SimpleAgent(randomSeed);
        }
        return null;
    }

}