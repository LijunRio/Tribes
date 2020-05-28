import core.Constants;
import core.Types;
import core.game.Game;
import core.actors.Tribe;
import core.game.TribeResult;
import players.*;
import players.heuristics.AlgParams;
import players.mc.MCParams;
import players.mc.MonteCarloAgent;
import players.mcts.MCTSParams;
import players.mcts.MCTSPlayer;
import players.oep.OEPAgent;
import players.oep.OEPParams;
import players.osla.OSLAParams;
import players.osla.OneStepLookAheadAgent;
import utils.MultiStatSummary;
import utils.StatSummary;

import java.util.*;

import static core.Types.GAME_MODE.*;
import static core.Types.TRIBE.*;

/**
 * Entry point of the framework.
 */
public class Play {

    public static boolean RUN_VERBOSE = true;
    public static long AGENT_SEED = -1, GAME_SEED = -1;

    enum PlayerType
    {
        DONOTHING,
        HUMAN,
        RANDOM,
        OSLA,
        MC,
        SIMPLE,
        MCTS,
        OEP;
    }

//    Game seed: 1590514560867
//    Agents random seed: 1590486463964
//    Level seed: 1591330872230


    private static Agent _getAgent(PlayerType playerType, long agentSeed, ActionController ac)
    {
        switch (playerType)
        {
            case DONOTHING: return new DoNothingAgent(agentSeed);
            case HUMAN: return new HumanAgent(ac);
            case RANDOM: return new RandomAgent(agentSeed);
            case OSLA:
                OSLAParams oslaParams = new OSLAParams();
                oslaParams.stop_type = oslaParams.STOP_FMCALLS; //Upper bound
                return new OneStepLookAheadAgent(agentSeed, oslaParams);
            case MC:
                MCParams mcparams = new MCParams();
                mcparams.stop_type = mcparams.STOP_FMCALLS;
//                mcparams.stop_type = mcparams.STOP_ITERATIONS;
                mcparams.PRIORITIZE_ROOT = false;
                return new MonteCarloAgent(agentSeed, mcparams);
            case SIMPLE: return new SimpleAgent(agentSeed);
            case MCTS:
                MCTSParams mctsParams = new MCTSParams();
                mctsParams.stop_type = mctsParams.STOP_FMCALLS;
                return new MCTSPlayer(agentSeed, mctsParams);
            case OEP:
                OEPParams oepParams = new OEPParams();
                return new OEPAgent(agentSeed, oepParams);
        }
        return null;
    }


    public static void main(String[] args) {

        Types.GAME_MODE gameMode = CAPITALS; //SCORE;

//        String filename = "levels/SampleLevel2p.csv";
//        String filename = "levels/SampleLevel.csv";
//        String filename = "levels/MinimalLevel.csv";
//        String filename = "levels/MinimalLevel_water.csv";
//        String filename = "levels/MinimalLevel2.csv";

        String saveGameFile = "save/1589357287411/4_0/game.json";


        //1. Play one game with visuals using the Level Generator:
//        AGENT_SEED = 1591455948310L; GAME_SEED = 1590563762657L;
        play(new Types.TRIBE[]{BARDUR, OUMAJI}, -1, new PlayerType[]{PlayerType.DONOTHING, PlayerType.HUMAN}, gameMode);
//        play(new Types.TRIBE[]{XIN_XI, IMPERIUS, BARDUR}, -1, new PlayerType[]{PlayerType.HUMAN, PlayerType.OSLA, PlayerType.OSLA}, gameMode);
//        play(new Types.TRIBE[]{XIN_XI, IMPERIUS, BARDUR, OUMAJI}, -1, new PlayerType[]{PlayerType.SIMPLE, PlayerType.SIMPLE, PlayerType.SIMPLE, PlayerType.SIMPLE}, gameMode);

        //2. Play one game with visuals from a file:
//        play(filename[0], new PlayerType[]{PlayerType.SIMPLE, PlayerType.OSLA, PlayerType.RANDOM, PlayerType.HUMAN}, gameMode);
//        play(filename[0], new PlayerType[]{PlayerType.SIMPLE, PlayerType.SIMPLE, PlayerType.SIMPLE, PlayerType.SIMPLE}, gameMode);
//        play(filename[0], new PlayerType[]{PlayerType.HUMAN, PlayerType.SIMPLE}, gameMode);


        //3. Play one game with visuals from a savegame
//        load(new PlayerType[]{PlayerType.SIMPLE, PlayerType.OSLA, PlayerType.RANDOM, PlayerType.OSLA}, saveGameFile);
    }

    private static void play(Types.TRIBE[] tribes, long levelSeed, PlayerType[] playerTypes, Types.GAME_MODE gameMode)
    {
        KeyController ki = new KeyController(true);
        ActionController ac = new ActionController();

        Game game = _prepareGame(tribes, levelSeed, playerTypes, gameMode, ac);
        Run.runGame(game, ki, ac);
    }

    private static void play(String levelFile, PlayerType[] playerTypes, Types.GAME_MODE gameMode)
    {
        KeyController ki = new KeyController(true);
        ActionController ac = new ActionController();

        Game game = _prepareGame(levelFile, playerTypes, gameMode, ac);
        Run.runGame(game, ki, ac);
    }


    private static void load(PlayerType[] playerTypes, String saveGameFile)
    {
        KeyController ki = new KeyController(true);
        ActionController ac = new ActionController();

        long agentSeed = AGENT_SEED == -1 ? System.currentTimeMillis() + new Random().nextInt() : AGENT_SEED;

        Game game = _loadGame(playerTypes, saveGameFile, agentSeed);
        Run.runGame(game, ki, ac);
    }


    private static Game _prepareGame(String levelFile, PlayerType[] playerTypes, Types.GAME_MODE gameMode, ActionController ac)
    {
        long gameSeed = GAME_SEED == -1 ? System.currentTimeMillis() : GAME_SEED;
        if(RUN_VERBOSE) System.out.println("Game seed: " + gameSeed);

        ArrayList<Agent> players = getPlayers(playerTypes, ac);

        Game game = new Game();
        game.init(players, levelFile, gameSeed, gameMode);
        return game;
    }

    private static Game _prepareGame(Types.TRIBE[] tribes, long levelSeed, PlayerType[] playerTypes, Types.GAME_MODE gameMode, ActionController ac)
    {
        long gameSeed = GAME_SEED == -1 ? System.currentTimeMillis() : GAME_SEED;

        if(RUN_VERBOSE) System.out.println("Game seed: " + gameSeed);

        ArrayList<Agent> players = getPlayers(playerTypes, ac);

        Game game = new Game();

        long levelGenSeed = levelSeed;
        if(levelGenSeed == -1)
            levelGenSeed = System.currentTimeMillis() + new Random().nextInt();

        if(RUN_VERBOSE) System.out.println("Level seed: " + levelGenSeed);

        game.init(players, levelGenSeed, tribes, gameSeed, gameMode);

        return game;
    }

    private static ArrayList<Agent> getPlayers(PlayerType[] playerTypes, ActionController ac)
    {
        ArrayList<Agent> players = new ArrayList<>();
        long agentSeed = AGENT_SEED == -1 ? System.currentTimeMillis() + new Random().nextInt() : AGENT_SEED;

        if(RUN_VERBOSE)  System.out.println("Agents random seed: " + agentSeed);

        for(int i = 0; i < playerTypes.length; ++i)
        {
            Agent ag = _getAgent(playerTypes[i], agentSeed, ac);
            ag.setPlayerID(i);
            players.add(ag);
        }
        return players;
    }

    private static Game _loadGame(PlayerType[] playerTypes, String saveGameFile, long agentSeed)
    {
        ArrayList<Agent> players = new ArrayList<>();

        for(int i = 0; i < playerTypes.length; ++i)
        {
            Agent ag = _getAgent(playerTypes[i], agentSeed, null);
            ag.setPlayerID(i);
            players.add(ag);
        }

        Game game = new Game();
        game.init(players, saveGameFile);
        return game;
    }

}