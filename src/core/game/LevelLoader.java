package core.game;

import core.Types;
import core.actors.City;
import core.actors.Tribe;
import core.actors.units.Unit;
import utils.Vector2d;

import java.awt.*;
import java.util.Random;

class LevelLoader
{

    private Dimension size;

    LevelLoader()
    {
        size = new Dimension();
    }

    /**
     * Builds a level, receiving a file name.
     * @param tribes tribes to play in this game
     * @param lines lines containing the level
     */
    Board buildLevel(Tribe[] tribes, String[] lines, Random rnd) {

        Board board = new Board();

        // Dimensions of the level read from the file.
        size.width = lines.length;
        size.height = lines.length;
        int tribesFound = 0;
        int numTribes = tribes.length;

        board.init(size.width, tribes);

        //Go through every token in the level file
        for (int i = 0; i < size.height; ++i) {
            String line = lines[i];

            String[] tile = line.split(",");
            for(int j = 0; j < tile.length; ++j)
            {
                //Format <terrain_char>:[<resource_char>]
                // (<resource_char> is optional)
                // Retrieve the chars and assign the corresponding enum values in the board.
                String[] tileSplit = tile[j].split(":");
                char terrainChar = tileSplit[0].charAt(0);
                boolean isCity = terrainChar == Types.TERRAIN.CITY.getMapChar();

                if(isCity)
                {
                    if(tribesFound==numTribes)
                    {
                        //If we've already allocated all the cities to the number of tribes, turn this extra city into a village.
                        terrainChar = Types.TERRAIN.VILLAGE.getMapChar();
                    }

                    //Second character indicates the tribe for this tribe
                    int tribeID = Integer.parseInt(tileSplit[1]);
                    //Types.TRIBE tribe = Types.TRIBE.getTypeByKey(tribeID);

                    //A city to create. Add it and assign it to the next tribe.
                    City c = new City(i, j, tribeID);
                    c.setCapital(true);
                    board.addCityToTribe(c,rnd);

                    //Also, each tribe starts with a unit in the same location where the city is
                    Types.UNIT unitType = tribes[tribeID].getType().getStartingUnit();
                    Unit unit = Types.UNIT.createUnit(new Vector2d(i,j), 0, false, c.getActorId(), tribeID, unitType);
                    board.addUnit(c, unit);

                    //City tiles
                    board.assignCityTiles(c, c.getBound());

                    tribesFound++;
                }

                board.setTerrainAt(i,j, Types.TERRAIN.getType(terrainChar));


                if(!isCity && tileSplit.length == 2)
                {
                    char resourceChar = tileSplit[1].charAt(0);
                    board.setResourceAt(i,j,Types.RESOURCE.getType(resourceChar));
                }
            }
        }

        return board;
    }


}
