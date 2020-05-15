package utils;

import javax.swing.*;
import java.awt.*;
import java.util.*;

import core.Types;
import core.actions.cityactions.CityAction;
import core.actions.cityactions.ResourceGathering;
import core.actions.unitactions.*;
import core.actors.Actor;
import core.actors.City;
import core.actors.Tribe;
import core.actors.units.Catapult;
import core.actors.units.SuperUnit;
import core.actors.units.Unit;
import core.game.Board;
import core.game.Game;
import core.game.GameState;
import core.actions.Action;

import static core.Constants.*;
import static core.Types.TERRAIN.*;
import static core.Types.UNIT.*;
import static utils.Vector2d.manhattanDistance;

@SuppressWarnings({"SuspiciousNameCombination", "unchecked"})
public class GameView extends JComponent {

    static int gridSize;
    private Game game;
    private Board board; //This only counts terrains. Needs to be enhanced with actors, resources, etc.
    private GameState gameState;
//    private Image backgroundImg;
    private Image fogImg, shineImg;
    private InfoView infoView;
    private Vector2d panTranslate;  // Used to translate all coordinates for objects drawn on screen

    private Color progressColor = new Color(53, 183, 255);
    private Color negativeColor = new Color(255, 63, 73);
    private Image starImg, starShadow, capitalImg, capitalShadow, cityWalls, road;

    boolean[][] actionable;

    // Action animations
    private Pair<Image, Vector2d> sourceAnimationInfo;
    private Pair<Image, Vector2d> targetAnimationInfo;
    private UnitAction animatedAction;
    private double animationSpeed;
    private Pair<Integer, Integer> actionAnimationUnitsTribe;

    private Image[] explosionEffect, pierceEffect;
    private Image[][] slashEffect, healEffect, convertEffect;  // Different per tribe
    private int effectDrawingIdx = -1, effectTribeIdx;
    private Vector2d effectPosition;
    private EFFECT effectType;  // What effect are we drawing?
    int delay = 5;
    final int nTilesExplosion = 12;
    final int nTilesEffect = 6;
    final int nTilesPierce = 3;

    enum EFFECT{
        EXPLOSION,
        SLASH,
        HEAL,
        CONVERT,
        PIERCE
    }

    /**
     * Dimensions of the window.
     */
    public static Dimension dimension;
    private static double isometricAngle = -45;

    GameView(Game game, InfoView inforView, Vector2d panTranslate)
    {
        this.game = game;
        this.board = game.getBoard().copy();
        this.infoView = inforView;
        this.panTranslate = panTranslate;

        gridSize = board.getSize();
//        int size = gridSize * CELL_SIZE;
//        int d = (int) Math.sqrt(size * size * 2);
        dimension = new Dimension(GUI_GAME_VIEW_SIZE, GUI_GAME_VIEW_SIZE);

//        backgroundImg = PLAIN.getImage(null);
        fogImg = ImageIO.GetInstance().getImage("img/fog.png");
        shineImg = ImageIO.GetInstance().getImage("img/shine3.png");
        starImg = ImageIO.GetInstance().getImage("img/decorations/star.png");
        starShadow = ImageIO.GetInstance().getImage("img/decorations/starShadow.png");
        capitalImg = ImageIO.GetInstance().getImage("img/decorations/capital.png");
        capitalShadow = ImageIO.GetInstance().getImage("img/decorations/capitalShadow.png");
        cityWalls = ImageIO.GetInstance().getImage("img/terrain/walls.png");
        road = ImageIO.GetInstance().getImage("img/terrain/road.png");

        int expLength = nTilesExplosion * delay;
        int pierceLength = nTilesPierce * delay;
        explosionEffect = new Image[expLength];
        pierceEffect = new Image[pierceLength];
        for (int i = 0; i < nTilesExplosion; i++) {
            for (int j = 0; j < delay; j++) {
                explosionEffect[i*delay+j] = ImageIO.GetInstance().getImage("img/weapons/effects/explosion/tile" + String.format("%03d", i) + ".png");
            }
        }
        for (int i = 0; i < nTilesPierce; i++) {
            for (int j = 0; j < delay; j++) {
                pierceEffect[i*delay+j] = ImageIO.GetInstance().getImage("img/weapons/effects/pierce/tile" + String.format("%03d", i) + ".png");
            }
        }
        int nPlayers = game.getPlayers().length;
        slashEffect = new Image[nPlayers][];
        healEffect = new Image[nPlayers][];
        convertEffect = new Image[nPlayers][];
        int effLength = nTilesEffect * delay;
        for (int j = 0; j < nPlayers; j++) {
            slashEffect[j%nPlayers] = new Image[effLength];
            healEffect[j%nPlayers] = new Image[effLength];
            convertEffect[j%nPlayers] = new Image[effLength];
            for (int i = 0; i < nTilesEffect; i++) {
                for (int k = 0; k < delay; k++) {
                    slashEffect[j % nPlayers][i*delay + k] = ImageIO.GetInstance().getImage("img/weapons/effects/slash/" + j + "/tile" + String.format("%03d", i) + ".png");
                    healEffect[j % nPlayers][i*delay + k] = ImageIO.GetInstance().getImage("img/weapons/effects/heal/" + j + "/tile" + String.format("%03d", i) + ".png");
                    convertEffect[j % nPlayers][i*delay + k] = ImageIO.GetInstance().getImage("img/weapons/effects/convert/" + j + "/tile" + String.format("%03d", i) + ".png");
                }
            }
        }
    }


    public void paintComponent(Graphics gx)
    {
        Graphics2D g = (Graphics2D) gx;
        paintWithGraphics(g);
    }

    private void paintWithGraphics(Graphics2D g)
    {
        if(gameState == null)
            return;

        //For a better graphics, enable this: (be aware this could bring performance issues depending on your HW & OS).
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g.setColor(Color.BLACK);
        g.fillRect(0, 0, dimension.width, dimension.height);

        // Update list of actionable tiles to be highlighted (collectible resources)
        updateActionableTiles();
        paintTerrains(g);
        paintRoads(g);
        paintResourcesBuildings(g);

        int highlightX = infoView.getHighlightX();
        int highlightY = infoView.getHighlightY();

        highlightTile(g, highlightX, highlightY);

        drawCityDecorations(g);
        paintUnits(g);
        paintActionsHighlightedUnit(g, highlightX, highlightY);
        paintOtherActions(g);
        paintActionAnimations(g);
        paintEffects(g);

        g.setColor(Color.BLACK);
        //player.draw(g); //if we want to give control to the agent to paint something (for debug), start here.
    }

    private void updateActionableTiles() {
        actionable = new boolean[gridSize][gridSize];
        HashMap<Integer, ArrayList<Action>> actions = gameState.getCityActions();
        for (Map.Entry<Integer, ArrayList<Action>> e: actions.entrySet()) {
            for (Action a: e.getValue()) {
                if (a instanceof CityAction) {
                    Vector2d pos = ((CityAction) a).getTargetPos();
                    if (pos != null) {
                        actionable[pos.x][pos.y] = a instanceof ResourceGathering;
                    }
                }
            }
        }
    }

    private void paintTerrains(Graphics2D g) {
        for(int i = 0; i < gridSize; ++i) {
            for(int j = 0; j < gridSize; ++j) {
                Types.TERRAIN t = board.getTerrainAt(i,j);
                if(t == null || t == FOG) {
                    paintImageRotated(g, j * CELL_SIZE, i * CELL_SIZE, fogImg, CELL_SIZE, panTranslate);
                } else {
                    Image toPaint = getContextImg(i, j, t);
                    paintImageRotated(g, j * CELL_SIZE, i * CELL_SIZE, toPaint, CELL_SIZE, panTranslate);
                }
            }
        }
    }

    private void paintRoads(Graphics2D g) {
        for(int i = 0; i < gridSize; ++i) {
            for (int j = 0; j < gridSize; ++j) {
                if (board.isRoad(i, j)) {
                    paintImageRotated(g, j * CELL_SIZE, i * CELL_SIZE, road, CELL_SIZE, panTranslate);
                }
            }
        }
    }

    private void paintResourcesBuildings(Graphics2D g) {
        for(int i = 0; i < gridSize; ++i) {
            for(int j = 0; j < gridSize; ++j) {
                Types.TERRAIN t = board.getTerrainAt(i,j);
                Types.RESOURCE r = board.getResourceAt(i,j);
                if (actionable[i][j]) paintImageRotated(g, j * CELL_SIZE, i * CELL_SIZE, shineImg, CELL_SIZE, panTranslate);
                int imgSize = (int) (CELL_SIZE * 0.75);
                paintImageRotated(g, j * CELL_SIZE, i * CELL_SIZE, (r == null) ? null : r.getImage(t), imgSize, panTranslate);

                Types.BUILDING b = board.getBuildingAt(i,j);
                if (b != null && b != Types.BUILDING.CUSTOM_HOUSE && !(b.isTemple())) imgSize = CELL_SIZE;
                paintImageRotated(g, j*CELL_SIZE, i*CELL_SIZE, (b == null) ? null : b.getImage(), imgSize, panTranslate);
            }
        }
    }

    private void highlightTile(Graphics2D g, int highlightX, int highlightY) {
        if (highlightX != -1) {
            Stroke oldStroke = g.getStroke();
            g.setColor(Color.BLUE);
            g.setStroke(new BasicStroke(3));

            Vector2d p = rotatePoint(highlightX, highlightY);
            drawRotatedRect(g, p.x, p.y, CELL_SIZE - 1, CELL_SIZE - 1, panTranslate);
            g.setStroke(oldStroke);
            g.setColor(Color.BLACK);
        }
    }

    private void paintUnits(Graphics2D g) {
        for(int i = 0; i < gridSize; ++i) {
            for(int j = 0; j < gridSize; ++j) {
                Unit u = board.getUnitAt(i,j);
                if (u != null) {
                    int imgSize = (int) (CELL_SIZE * 0.75);
                    if (u instanceof SuperUnit || u instanceof Catapult) imgSize = CELL_SIZE;
                    String imgFile = u.getType().getImageFile();

                    Vector2d rotated = rotatePoint(j, i);
                    int x = rotated.x + CELL_SIZE*CELL_SIZE/4/imgSize;
                    int y = (int)(rotated.y - imgSize/1.5);

                    ArrayList<Action> possibleActions = gameState.getUnitActions(u);
                    boolean exhausted = (possibleActions == null || possibleActions.size() == 0);

                    Tribe t = gameState.getTribe(u.getTribeId());
                    int imageTribeId = t.getType().getKey();

                    if (exhausted) {
                        String exhaustedStr = imgFile + imageTribeId + "Exhausted.png";
                        Image exhaustedImg = ImageIO.GetInstance().getImage(exhaustedStr);
                        paintImage(g, x, y, exhaustedImg, imgSize, panTranslate);
                    } else {
                        paintImage(g, x, y, u.getType().getImage(imageTribeId), imgSize, panTranslate);
                    }
                }
            }
        }
    }

    private void paintActionsHighlightedUnit(Graphics2D g, int highlightX, int highlightY) {
        if (infoView.highlightInGridBounds()) {
            Unit u = board.getUnitAt(highlightY, highlightX);
            if (u != null && !infoView.clickedTwice()) {
                ArrayList<Action> possibleActions = gameState.getUnitActions(u);
                if (possibleActions != null && possibleActions.size() > 0) {
                    for (Action a : possibleActions) {
                        if (!(a instanceof Examine || a instanceof Capture)) {
                            Image actionImg = Types.ACTION.getImage(a);

                            if (actionImg != null) {
                                Vector2d pos = GUI.getActionPosition(gameState, a);

                                if (pos != null) {
                                    paintImageRotated(g, pos.y * CELL_SIZE, pos.x * CELL_SIZE, actionImg, CELL_SIZE, panTranslate);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private void paintOtherActions(Graphics2D g) {
        HashMap<Integer, ArrayList<Action>> actions = gameState.getUnitActions();
        for (Map.Entry<Integer, ArrayList<Action>> e: actions.entrySet()) {
            for (Action a : e.getValue()) {
                if (a instanceof Examine || a instanceof Capture) {
                    Image actionImg = Types.ACTION.getImage(a);
                    if (actionImg != null) {
                        Vector2d pos = GUI.getActionPosition(gameState, a);

                        if (pos != null) {
                            Vector2d rotated = rotatePoint(pos.y, pos.x);
                            int imgSize = (int)(CELL_SIZE*0.5);
                            paintImage(g, rotated.x + CELL_SIZE, rotated.y - imgSize/2,
                                    actionImg, imgSize, panTranslate);
                        }
                    }
                }
            }
        }
    }

    // *****************************************************************************************************************

    private static void paintImageRotated(Graphics2D gphx, int x, int y, Image img, int imgSize, Vector2d panTranslate) {
        if (img != null) {
            int w = img.getWidth(null);
            int h = img.getHeight(null);
            float scaleX = (float)imgSize/w;
            float scaleY = (float)imgSize/h;

            Graphics2D g2 = (Graphics2D)gphx.create();
            g2.translate(panTranslate.x, panTranslate.y + dimension.width/2.0);
            g2.rotate(Math.toRadians(isometricAngle));
            g2.drawImage(img, (int)(x + CELL_SIZE/2.0 - imgSize/2.0),
                    (int)(y + CELL_SIZE/2.0 - imgSize/2.0),
                    (int) (w*scaleX), (int) (h*scaleY), null);
            g2.dispose();
        }
    }

    private static void paintImageRotated(Graphics2D gphx, int x, int y, Image img, int imgSize, Vector2d panTranslate,
                                          double angle) {
        if (img != null) {
            int w = img.getWidth(null);
            int h = img.getHeight(null);
            float scaleX = (float)imgSize/w;
            float scaleY = (float)imgSize/h;

            Graphics2D g2 = (Graphics2D)gphx.create();
            g2.translate(panTranslate.x, panTranslate.y);
            g2.rotate(angle, x + imgSize/2.0, y + imgSize/2.0);
            g2.drawImage(img, x, y, (int) (w*scaleX), (int) (h*scaleY), null);
            g2.dispose();
        }
    }

    private static void paintImage(Graphics2D gphx, int x, int y, Image img, int imgSize, Vector2d panTranslate)
    {
        if (img != null) {
            int w = img.getWidth(null);
            int h = img.getHeight(null);
            float scaleX = (float)imgSize/w;
            float scaleY = (float)imgSize/h;
            gphx.drawImage(img, x + panTranslate.x, y + panTranslate.y,
                    (int) (w*scaleX), (int) (h*scaleY), null);
        }
    }

    private static void drawRotatedRect(Graphics2D g, int x, int y, int width, int height, Vector2d panTranslate) {
        Graphics2D g2 = (Graphics2D) g.create();
        x += panTranslate.x;
        y += panTranslate.y;
        g2.rotate(Math.toRadians(isometricAngle), x, y);
        g2.drawRect(x, y, width, height);
        g2.dispose();
    }

    /**
     * Expects coordinates in grid, translates to screen coordinates.
     */
    public static Vector2d rotatePoint(double x, double y) {
        double d = Math.sqrt(2*CELL_SIZE*CELL_SIZE);
        double x2 = x * CELL_SIZE + y * d/2 - x * d/5;
        double y2 = y * CELL_SIZE - y * d/5 - x * d/2;
        y2 += dimension.width/2.0;
        return new Vector2d((int)x2, (int)y2);
    }

    /**
     * Expects screen coordinates, returns coordinates in grid.
     */
    public static Vector2d rotatePointReverse(double x, double y) {
        double d = Math.sqrt(2*CELL_SIZE*CELL_SIZE);
        y -= GameView.dimension.width/2.0;
        double x2 = (x*(CELL_SIZE-d/5.0) - y*d/2.0)/(CELL_SIZE*CELL_SIZE - 2*CELL_SIZE*d/5.0 + (d/5)*(d/5) + d*d/4.0);
        double y2 = (y + x2 * d/2.0)/(CELL_SIZE - d/5.0);
        return new Vector2d((int)x2, (int)y2);
    }

    private void drawCityDecorations(Graphics2D g) {
        for(int i = 0; i < gridSize; ++i) {
            for (int j = 0; j < gridSize; ++j) {
                Types.TERRAIN terrainAt = board.getTerrainAt(i, j);
                if (terrainAt == CITY) {
                    int d = (int)Math.sqrt(CELL_SIZE*CELL_SIZE*2);
                    int fontSize = CELL_SIZE/3;
                    Font textFont = new Font(getFont().getName(), Font.PLAIN, fontSize);
                    g.setFont(textFont);

                    int cityID = board.getCityIdAt(i,j);
                    City c = (City) board.getActor(cityID);

                    if (c != null) {
                        int cityCapacity = c.getLevel() + 1;
                        int progress = c.getPopulation();
                        int units = c.getUnitsID().size();
                        Tribe tr = gameState.getTribe(c.getTribeId());
                        Color col = Types.TRIBE.values()[tr.getType().getKey()].getColorDark();
                        Color colTransparent = new Color(col.getRed(), col.getGreen(), col.getBlue(), 170);

                        // Draw city walls
                        if (c.hasWalls()) {
                            Vector2d rotatedP = rotatePoint(j + 0.4, i - 0.2);
                            paintImage(g, rotatedP.x, rotatedP.y, cityWalls, CELL_SIZE, panTranslate);
                        }

                        // Draw city border
                        LinkedList<Vector2d> tiles = board.getCityTiles(cityID);
                        int nCityTiles = tiles.size();
                        int nNeighbours = 4;
                        boolean[][] tileNeighbours = new boolean[nCityTiles][nNeighbours];  // order: left, right, up, down
                        Pair<Vector2d, Vector2d>[] lines = new Pair[]{new Pair<>(new Vector2d(0, 0), new Vector2d(0, 1)),
                                new Pair<>(new Vector2d(1, 0), new Vector2d(1, 1)),
                                new Pair<>(new Vector2d(0, 0), new Vector2d(1, 0)),
                                new Pair<>(new Vector2d(0, 1), new Vector2d(1, 1))};
                        for (int k = 0; k < nCityTiles-1; k++) {
                            Vector2d t1 = tiles.get(k);
                            for (int p = k+1; p < nCityTiles; p++) {
                                Vector2d t2 = tiles.get(p);
                                if (t1.equals(t2)) continue;
                                if (t1.x - t2.x == 1 && t1.y == t2.y) {  // t1 to the right of t2
                                    tileNeighbours[k][0] = true;
                                    tileNeighbours[p][1] = true;
                                } else if (t1.x - t2.x == -1 && t1.y == t2.y) { // t1 to the left of t2
                                    tileNeighbours[k][1] = true;
                                    tileNeighbours[p][0] = true;
                                } else if (t1.x == t2.x && t1.y - t2.y == 1) {  // t1 underneath t2
                                    tileNeighbours[k][2] = true;
                                    tileNeighbours[p][3] = true;
                                } else if (t1.x == t2.x && t1.y - t2.y == -1) {  // t1 above t2
                                    tileNeighbours[k][3] = true;
                                    tileNeighbours[p][2] = true;
                                }
                            }
                        }
                        g.setColor(col);
                        Stroke oldStroke = g.getStroke();
                        g.setStroke(new BasicStroke(5, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[]{9}, 0));
                        // Draw lines for tiles that don't have a neighbour on a side
                        for (int t = 0; t < nCityTiles; t++) {
                            Vector2d tile = tiles.get(t);
                            for (int n = 0; n < nNeighbours; n++) {
                                if (!tileNeighbours[t][n]) {
                                    // draw line on this side
                                    Vector2d from = rotatePoint(lines[n].getFirst().y + tile.y, lines[n].getFirst().x + tile.x);
                                    Vector2d to = rotatePoint(lines[n].getSecond().y + tile.y, lines[n].getSecond().x + tile.x);
                                    g.drawLine(from.x + panTranslate.x, from.y + panTranslate.y,
                                            to.x + panTranslate.x, to.y + panTranslate.y);
                                }
                            }
                        }
                        g.setStroke(oldStroke);

                        // Draw capital img + city name/ID + number of stars
                        String cityName = "" + cityID;
                        String production = "" + c.getProduction();

                        int sections = 2;
                        if (c.isCapital()) {
                            sections = 3;
                        }
                        int h = d / 4;
                        double nameWidth = GUI_CITY_TAG_WIDTH + sections * h;
                        Vector2d namePos = rotatePoint(j, i);
                        Rectangle nameRect = new Rectangle((int) (namePos.x + d / 2.0 - nameWidth * 2 / 3.0),
                                (int) (namePos.y + d / 2.0 - h), (int) nameWidth, h);
                        g.setColor(colTransparent);
                        g.fillRect(nameRect.x + panTranslate.x, nameRect.y + panTranslate.y, nameRect.width, nameRect.height);
                        g.setColor(Color.WHITE);

                        g.drawString(cityName, (int) (nameRect.x + (sections-2) * h + fontSize / 4.0 + panTranslate.x),
                                (int) (nameRect.y + h * 1.1 - fontSize / 4.0 + panTranslate.y));

                        // Draw number of stars
                        paintImage(g, (int) (nameRect.x + nameRect.width * (0.35 + (sections-2)*0.2) + SHADOW_OFFSET),
                                nameRect.y + SHADOW_OFFSET, starShadow, h, panTranslate);
                        paintImage(g, (int) (nameRect.x + nameRect.width * (0.35 + (sections-2)*0.2)), nameRect.y, starImg, h, panTranslate);
                        drawStringShadow(g, production, (int) (nameRect.x + nameRect.width - fontSize * 0.75),
                                (int) (nameRect.y + h * 1.1 - fontSize / 4.0));
                        g.setColor(Color.WHITE);
                        g.drawString(production, (int) (nameRect.x + nameRect.width - fontSize * 0.75 + panTranslate.x),
                                (int) (nameRect.y + h * 1.1 - fontSize / 4.0 + panTranslate.y));

                        // Draw capital sign
                        if (c.isCapital()) {
                            paintImage(g, nameRect.x + SHADOW_OFFSET, nameRect.y + SHADOW_OFFSET, capitalShadow, h, panTranslate);
                            paintImage(g, nameRect.x, nameRect.y, capitalImg, h, panTranslate);
                        }

                        // Draw level
                        h /= 2;
                        int sectionWidth = h;
                        int w = cityCapacity * sectionWidth;
                        Rectangle bgRect = new Rectangle(nameRect.x + nameRect.width / 2 - w / 2, nameRect.y + nameRect.height, w, h);
                        drawRoundRectShadowHighlight(g, bgRect);
                        g.setColor(Color.WHITE);
                        g.fillRoundRect(bgRect.x + panTranslate.x, bgRect.y + panTranslate.y,
                                bgRect.width, bgRect.height, ROUND_RECT_ARC, ROUND_RECT_ARC);

                        // Draw population/progress
                        if (progress >= 0) {
                            g.setColor(progressColor);
                        } else {
                            g.setColor(negativeColor);
                        }
                        int pw = Math.abs(progress) * sectionWidth;
                        Rectangle pgRect = new Rectangle(bgRect.x, bgRect.y, pw, bgRect.height);
                        g.fillRoundRect(pgRect.x + panTranslate.x, pgRect.y + +panTranslate.y,
                                pgRect.width, pgRect.height, ROUND_RECT_ARC, ROUND_RECT_ARC);

                        // Draw unit counts
                        g.setColor(Color.black);
                        int radius = h / 2;
                        int unitHeight = bgRect.y + h / 2 - radius / 2;
                        for (int u = 0; u < units; u++) {
                            g.fillOval(bgRect.x + sectionWidth * u + sectionWidth / 2 - radius / 2 + panTranslate.x,
                                    unitHeight + panTranslate.y, radius, radius);
                        }

                        // Draw section separations
                        for (int l = 0; l < cityCapacity - 1; l++) {
                            int lx = bgRect.x + sectionWidth * (l + 1);
                            g.drawLine(lx + panTranslate.x, bgRect.y + panTranslate.y,
                                    lx + panTranslate.x, bgRect.y + bgRect.height + panTranslate.y);
                        }
                    }
                }
            }
        }
    }

    private void drawRoundRectShadowHighlight(Graphics2D g, Rectangle rect) {
        g.setColor(new Color(0, 0, 0, 122));
        g.fillRoundRect(rect.x + SHADOW_OFFSET + panTranslate.x,
                rect.y + SHADOW_OFFSET + panTranslate.y, rect.width, rect.height,
                ROUND_RECT_ARC, ROUND_RECT_ARC);
        g.setColor(new Color(255, 255, 255, 122));
        g.fillRoundRect(rect.x - SHADOW_OFFSET + panTranslate.x,
                rect.y - SHADOW_OFFSET + panTranslate.y, rect.width, rect.height,
                ROUND_RECT_ARC, ROUND_RECT_ARC);
    }

    private void drawStringShadow (Graphics2D g, String s, int x, int y) {
        g.setColor(new Color(0, 0, 0, 122));
        g.drawString(s, x+SHADOW_OFFSET + panTranslate.x, y+SHADOW_OFFSET + panTranslate.y);
    }


    /**
     * Paints the board
     * @param gs current game state
     */
    void paint(GameState gs)
    {
        //The tribe Id of which the turn gs at this point
        //int gameTurn = 0;// gs.getTick() % gs.getTribes().length;
        gameState = gs; //.copy(gameTurn);
        board = gameState.getBoard();
    }

    void updatePan(Vector2d panTranslate) {
        this.panTranslate = new Vector2d(this.panTranslate.x + panTranslate.x,
                this.panTranslate.y + panTranslate.y);
    }

    void setPanToTribe(GameState gs) {
        // Focus on capital of tribe
        Tribe t = gs.getTribe(gs.getActiveTribeID());
        int capitalID = t.getCapitalID();
        Actor a = gs.getActor(capitalID);
        Vector2d pos = a.getPosition();

        // Get position in screen coordinates, and set pan to the negative difference to center
        Vector2d screenPoint = rotatePoint(pos.y, pos.x);
        panTranslate = new Vector2d(-screenPoint.x - CELL_SIZE/2 + dimension.width/2,
                -screenPoint.y - CELL_SIZE/2 + dimension.height/2);
    }

    public Vector2d getPanTranslate() {
        return panTranslate;
    }

    /**
     * Gets the dimensions of the window.
     * @return the dimensions of the window.
     */
    public Dimension getPreferredSize() {
        return dimension;
    }


    private Image getContextImg(int i, int j, Types.TERRAIN t) {
        Image toPaint;
        boolean cornerUL = (i == 0 && j == 0);
        boolean cornerDR = (i == gridSize - 1 && j == gridSize -1);
        Types.TERRAIN diagUR = null;
        if (i > 0 && j < gridSize - 1) diagUR = board.getTerrainAt(i - 1, j + 1);

        if (t == DEEP_WATER || t == SHALLOW_WATER) {
            // If this is the last tile on the row
            boolean down = (i == gridSize - 1);
            // If the tile above is not water
            boolean top = (i > 0 && board.getTerrainAt(i - 1, j) != SHALLOW_WATER && board.getTerrainAt(i - 1, j) != DEEP_WATER);
            // If first tile on column
            boolean left = (j == 0);
            // If the tile to the right is not water
            boolean right = (j < gridSize - 1 && board.getTerrainAt(i, j + 1) != SHALLOW_WATER && board.getTerrainAt(i, j + 1) != DEEP_WATER);
            if (down) {
                if (left) {
                    toPaint = t.getImage("down-left");
                } else if (right) {
                    if (top) {
                        toPaint = t.getImage("top-down-right");
                    } else {
                        if (t == SHALLOW_WATER && (diagUR == SHALLOW_WATER || diagUR == DEEP_WATER)) {
                            toPaint = t.getImage("down-right-ur");
                        } else {
                            toPaint = t.getImage("down-right");
                        }
                    }
                } else if (top) {
                    if (t == SHALLOW_WATER && (diagUR == SHALLOW_WATER || diagUR == DEEP_WATER)) {
                        toPaint = t.getImage("top-down-ur");
                    } else {
                        toPaint = t.getImage("top-down");
                    }
                } else {
                    toPaint = t.getImage("down");
                }
            } else if (top) {
                if (t == SHALLOW_WATER && (j == gridSize-1 || diagUR == SHALLOW_WATER || diagUR == DEEP_WATER)) {
                    if (left) {
                        toPaint = t.getImage("top-left-ur");
                    } else {
                        toPaint = t.getImage("top-ur");
                    }
                } else {
                    if (right) {
                        if (left) {
                            toPaint = t.getImage("top-left-right");
                        } else {
                            toPaint = t.getImage("top-right");
                        }
                    } else if (left) {
                        toPaint = t.getImage("top-left");
                    } else {
                        toPaint = t.getImage("top");
                    }
                }
            } else if (right) {
                if (i == 0 || j < gridSize - 1 && t == SHALLOW_WATER && (diagUR == SHALLOW_WATER || diagUR == DEEP_WATER)) {
                    toPaint = t.getImage("right-ur");
                } else {
                    toPaint = t.getImage("right");
                }
            } else if (left) {
                toPaint = t.getImage("left");
            } else {
                toPaint = t.getImage(null);
            }
        } else {
            // If this is the last tile on the row, or the tile underneath is water, get the '-down' img
            boolean down = (i == gridSize - 1 || board.getTerrainAt(i+1, j) == SHALLOW_WATER);
            // If the same is true for the column instead, this is a '-left' img
            boolean left = (j == 0 || board.getTerrainAt(i, j-1) == SHALLOW_WATER);

            if (down) {
                if (j == gridSize - 1) {
                    if (cornerDR) {
                        toPaint = t.getImage("corner-dr");
                    } else {
                        toPaint = t.getImage("down-dr");
                    }
                } else {
                    if (left) {
                        if (j == 0) {
                            if (i < gridSize - 1 && (board.getTerrainAt(i, j + 1) == SHALLOW_WATER ||
                                            board.getTerrainAt(i, j + 1) == DEEP_WATER)) {
                                toPaint = t.getImage("down-left-el-dr");
                            } else {
                                toPaint = t.getImage("down-left-el");
                            }
                        } else if (i == gridSize - 1) {
                            if (board.getTerrainAt(i - 1, j) == SHALLOW_WATER
                                    || board.getTerrainAt(i - 1, j) == DEEP_WATER) {
                                toPaint = t.getImage("down-left-ed-ul");
                            } else {
                                toPaint = t.getImage("down-left-ed");
                            }
                        } else {
                            toPaint = t.getImage("down-left");
                        }
                    } else {
                        if (j < gridSize - 1 && i < gridSize - 1 &&
                                (board.getTerrainAt(i, j + 1) == SHALLOW_WATER ||
                                board.getTerrainAt(i, j + 1) == DEEP_WATER)) {
                            toPaint = t.getImage("down-dr");
                        } else {
                            toPaint = t.getImage("down");
                        }
                    }
                }
            } else if (left) {
                if (i == 0 || board.getTerrainAt(i - 1, j) == SHALLOW_WATER ||
                        board.getTerrainAt(i - 1, j) == DEEP_WATER) {
                    if (cornerUL) {
                        toPaint = t.getImage("corner-ul");
                    } else {
                        if (j == 0) {
                            toPaint = t.getImage("left");
                        } else {
                            toPaint = t.getImage("left-ul");
                        }
                    }
                } else {
                    toPaint = t.getImage("left");
                }
            } else {
                Types.TERRAIN tl = board.getTerrainAt(i, j - 1);
                Types.TERRAIN td = board.getTerrainAt(i + 1, j);
                Types.TERRAIN tld = board.getTerrainAt(i + 1, j - 1);
                if (i < gridSize-1 && j > 0 && (tld == SHALLOW_WATER || tld == DEEP_WATER) &&
                        (tl != SHALLOW_WATER && tl != DEEP_WATER) &&
                        (td != SHALLOW_WATER && td != DEEP_WATER)) {
                    toPaint = t.getImage("dl");
                } else {
                    toPaint = t.getImage(null);
                }
            }
        }
        return toPaint;
    }

    // TODO: can draw more effects of actions, e.g. healing, disband
    void paintEffects(Graphics2D g) {
        if (effectDrawingIdx > -1) {
            Image effectImage = null;
            if (effectType == EFFECT.EXPLOSION) {
                if (effectDrawingIdx == explosionEffect.length) {
                    effectDrawingIdx = -1;  // Finished
                    game.setPaused(false);
                    return;
                }
                effectImage = explosionEffect[effectDrawingIdx];
            } else if (effectType == EFFECT.PIERCE) {
                if (effectDrawingIdx == pierceEffect.length) {
                    effectDrawingIdx = -1;  // Finished
                    game.setPaused(false);
                    return;
                }
                effectImage = pierceEffect[effectDrawingIdx];
            } else if (effectType == EFFECT.SLASH) {
                if (effectDrawingIdx == slashEffect[effectTribeIdx].length) {
                    effectDrawingIdx = -1;  // Finished
                    game.setPaused(false);
                    return;
                }
                effectImage = slashEffect[effectTribeIdx][effectDrawingIdx];
            } else if (effectType == EFFECT.HEAL) {
                if (effectDrawingIdx == healEffect[effectTribeIdx].length) {
                    effectDrawingIdx = -1;  // Finished
                    game.setPaused(false);
                    return;
                }
                effectImage = healEffect[effectTribeIdx][effectDrawingIdx];
            } else if (effectType == EFFECT.CONVERT) {
                if (effectDrawingIdx == convertEffect[effectTribeIdx].length) {
                    effectDrawingIdx = -1;  // Finished
                    game.setPaused(false);
                    return;
                }
                effectImage = convertEffect[effectTribeIdx][effectDrawingIdx];
            }

            // Draw effect
            if (effectImage != null) {
                Vector2d rotated = rotatePoint(1.0 * effectPosition.x / CELL_SIZE, 1.0 * effectPosition.y / CELL_SIZE);
                paintImage(g, rotated.x + CELL_SIZE / 5, rotated.y - CELL_SIZE/2, effectImage, CELL_SIZE, panTranslate);
                if (effectType == EFFECT.SLASH || effectType == EFFECT.CONVERT) {
                    paintImageRotated(g, rotated.x + CELL_SIZE / 5, rotated.y - CELL_SIZE / 2, effectImage, CELL_SIZE, panTranslate, Math.PI / 2);
                }
                effectDrawingIdx++;
            } else {
                effectDrawingIdx = -1;
                game.setPaused(false);
            }
        }
    }

    void paintAction(UnitAction a) {
        if (a instanceof Attack || a instanceof Convert || a instanceof HealOthers) {  // These are the actions currently animated
            Unit source = (Unit) gameState.getBoard().getActor(a.getUnitId());
            Image weapon1 = source.getType().getWeaponImage(source.getTribeId());

            if (weapon1 != null) {
                // Pause the game, paint this weapon image travelling from attacker to target
                game.setPaused(true);
                this.sourceAnimationInfo = new Pair<>(weapon1, new Vector2d(source.getPosition().y * CELL_SIZE, source.getPosition().x * CELL_SIZE));
                Unit target;
                Image weapon2 = null;

                if (a instanceof Attack) {
                    target = (Unit) gameState.getBoard().getActor(((Attack) a).getTargetId());
                    weapon2 = target.getType().getWeaponImage(target.getTribeId());  // units can retaliate in Attack actions

                    this.effectType = EFFECT.SLASH;
                    if (source.getType() == CATAPULT || source.getType() == SHIP || source.getType() == BATTLESHIP) {
                        this.effectType = EFFECT.EXPLOSION;
                    } else if (source.getType() == ARCHER || source.getType() == BOAT) {
                        this.effectType = EFFECT.PIERCE;
                    }
                } else if (a instanceof Convert) {
                    target = (Unit) gameState.getBoard().getActor(((Convert) a).getTargetId());
                    this.effectType = EFFECT.CONVERT;
                } else {
                    // Heal Others
                    this.effectType = EFFECT.HEAL;
                    ArrayList<Unit> targets = ((HealOthers) a).getTargets(gameState);
                    target = targets.get(0);  // TODO: draw effect to all units
                }

                if (target != null) {
                    this.targetAnimationInfo = new Pair<>(weapon2, new Vector2d(target.getPosition().y * CELL_SIZE, target.getPosition().x * CELL_SIZE));
                    this.animatedAction = a;
                    this.animationSpeed = Math.min(0.5, manhattanDistance(source.getPosition(), target.getPosition()) * 0.05);
                    this.actionAnimationUnitsTribe = new Pair<>(source.getTribeId(), target.getTribeId());
                } else {
                    sourceAnimationInfo = null;
                }
            }
        }
    }

    private void paintActionAnimations(Graphics2D g) {
        if (sourceAnimationInfo != null) {
            // Sprite not yet reached its destination, paint current and calculate next
            Vector2d currentPosition = sourceAnimationInfo.getSecond().copy();

            // Next position, move closer to target
            int xDir = (int)(CELL_SIZE * animationSpeed *  Math.signum(targetAnimationInfo.getSecond().x - currentPosition.x));
            int yDir = (int)(CELL_SIZE * animationSpeed * Math.signum(targetAnimationInfo.getSecond().y - currentPosition.y));
            sourceAnimationInfo.getSecond().add(xDir, yDir);
            Vector2d nextPosition = sourceAnimationInfo.getSecond().copy();

            // Rotate image in direction of travel
            double dx = nextPosition.x - currentPosition.x;
            double dy = nextPosition.y - currentPosition.y;
            double imageAngleRad = Math.atan2(dx, dy);// + Math.toRadians(180);

            Vector2d rotated = rotatePoint(1.0*currentPosition.x/CELL_SIZE, 1.0*currentPosition.y/CELL_SIZE);
            paintImageRotated(g, rotated.x + CELL_SIZE/2, rotated.y - CELL_SIZE/4, sourceAnimationInfo.getFirst(), CELL_SIZE/2, panTranslate, imageAngleRad);

            if (currentPosition.equalsPlusError(targetAnimationInfo.getSecond(), CELL_SIZE* animationSpeed)) {
                // Reached destination, no more drawing. Reset animation variables and unpause game, unless retaliation happening

                // Draw end of animation effect
                effectPosition = targetAnimationInfo.getSecond();
                effectTribeIdx = actionAnimationUnitsTribe.getFirst();
                effectDrawingIdx = 0;

                if (animatedAction instanceof Attack && targetAnimationInfo.getFirst() != null && ((Attack) animatedAction).isRetaliation(gameState)) {
                    // Retaliating! Reset variables to target's attack
                    Vector2d startPosition = targetAnimationInfo.getSecond().copy();
                    Vector2d targetPosition = board.getActor(animatedAction.getUnitId()).getPosition().copy();
                    Vector2d endPosition = new Vector2d(targetPosition.y * CELL_SIZE, targetPosition.x * CELL_SIZE);
                    sourceAnimationInfo = new Pair<>(targetAnimationInfo.getFirst(), startPosition);
                    targetAnimationInfo = new Pair<>(null, endPosition);
                    actionAnimationUnitsTribe.swap();
                } else {
                    // No more of this animation
                    sourceAnimationInfo = null;
                }
            }
        }
    }

    Action getAnimatedAction() {
        if (sourceAnimationInfo == null && animatedAction != null) {
            Action a = animatedAction.copy();
            animatedAction = null;
            return a;
        }
        return null;
    }
}
