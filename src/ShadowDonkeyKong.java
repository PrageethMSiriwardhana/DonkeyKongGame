import java.io.*;

import bagel.*;
import bagel.util.Point;

import java.util.*;
import java.util.Properties;
import java.util.List;
import java.util.HashSet;
import java.util.Set;


public class ShadowDonkeyKong extends AbstractGame {
    private final Properties gameProps;
    private final Properties messageProps;

    private GameState state = GameState.HOME;
    private final Font titleFont;
    private final Font promptFont;
    private final Font scoreFont;
    private final Image background;

    private final Mario mario;
    private final DonkeyKong donkey;
    private final Hammer hammer;
    private final List<Barrel> barrels = new ArrayList<>();
    private final List<Platform> platforms = new ArrayList<>();
    private final List<Ladder> ladders = new ArrayList<>();

    private final ScoreManager scoreManager = new ScoreManager();
    private int currentFrame = 0;

    public ShadowDonkeyKong(Properties gameProps, Properties messageProps) {
        super(GameUtils.getIntProperty(gameProps, "window.width", 1024),
                GameUtils.getIntProperty(gameProps, "window.height", 768),
                messageProps.getProperty("home.title"));

        this.gameProps = gameProps;
        this.messageProps = messageProps;

        background = new Image(gameProps.getProperty("backgroundImage"));
        titleFont = new Font(gameProps.getProperty("font"),
                GameUtils.getIntProperty(gameProps, "home.title.fontSize", 64));
        promptFont = new Font(gameProps.getProperty("font"),
                GameUtils.getIntProperty(gameProps, "home.prompt.fontSize", 24));
        scoreFont = new Font(gameProps.getProperty("font"),
                GameUtils.getIntProperty(gameProps, "gamePlay.score.fontSize", 20));

        mario = new Mario(gameProps);
        donkey = new DonkeyKong(gameProps);
        hammer = new Hammer(gameProps);

        int barrelCount = GameUtils.getIntProperty(gameProps, "barrel.count", 0);
        for (int i = 1; i <= barrelCount; i++) {
            String key = "barrel." + i;
            if (gameProps.containsKey(key)) {
                String[] coords = gameProps.getProperty(key).split(",");
                if (coords.length == 2) {
                    double x = Double.parseDouble(coords[0]);
                    double y = Double.parseDouble(coords[1]);
                    boolean shouldFall = (i == 3 || i == 4);
                    double startY = y;

                    if (i == 3) startY = 300;
                    if (i == 4) startY = 250;

                    barrels.add(new Barrel(new Point(x, y), shouldFall, startY));
                }
            }
        }


        int ladderCount = GameUtils.getIntProperty(gameProps, "ladder.count", 0);
        for (int i = 1; i <= ladderCount; i++) {
            String key = "ladder." + i;
            if (gameProps.containsKey(key)) {
                String[] coords = gameProps.getProperty(key).split(",");
                if (coords.length == 2) {
                    double x = Double.parseDouble(coords[0]);
                    double y = Double.parseDouble(coords[1]);
                    ladders.add(new Ladder(new Point(x, y)));
                }
            }
        }

        if (gameProps.containsKey("platforms")) {
            String[] parts = gameProps.getProperty("platforms").split(";");
            for (String part : parts) {
                String[] coords = part.split(",");
                if (coords.length == 2) {
                    double x = Double.parseDouble(coords[0]);
                    double y = Double.parseDouble(coords[1]);
                    platforms.add(new Platform(new Point(x, y)));
                }
            }
        }
    }

    @Override
    protected void update(Input input) {
        if (input.wasPressed(Keys.ESCAPE)) {
            Window.close();
        }

        switch (state) {
            case HOME:
                drawHomeScreen();
                if (input.wasPressed(Keys.ENTER)) {
                    resetGame();
                    state = GameState.GAMEPLAY;
                }

                break;
            case GAMEPLAY:
                updateGameplay(input);
                break;
            case GAME_OVER:
                drawGameOverScreen();
                if (input.wasPressed(Keys.SPACE)) {
                    state = GameState.HOME;
                    currentFrame = 0;
                }
                break;
        }

    }

    private void drawHomeScreen() {
        background.draw(Window.getWidth() / 2.0, Window.getHeight() / 2.0);
        titleFont.drawString(
                messageProps.getProperty("home.title"),
                Window.getWidth() / 2.0 - titleFont.getWidth(messageProps.getProperty("home.title")) / 2,
                GameUtils.getIntProperty(gameProps, "home.title.y", 384)
        );
        promptFont.drawString(
                messageProps.getProperty("home.prompt"),
                Window.getWidth() / 2.0 - promptFont.getWidth(messageProps.getProperty("home.prompt")) / 2,
                GameUtils.getIntProperty(gameProps, "home.prompt.y", 550)
        );
    }

    private void resetGame() {
        currentFrame = 0;
        scoreManager.reset();

        mario.reset(gameProps);
        donkey.reset(gameProps);

        hammer.reset(gameProps);
        barrels.clear();

        int barrelCount = GameUtils.getIntProperty(gameProps, "barrel.count", 0);
        for (int i = 1; i <= barrelCount; i++) {
            String key = "barrel." + i;
            if (gameProps.containsKey(key)) {
                String[] coords = gameProps.getProperty(key).split(",");
                if (coords.length == 2) {
                    double x = Double.parseDouble(coords[0]);
                    double y = Double.parseDouble(coords[1]);

                    boolean shouldFall = (i == 3 || i == 4);
                    double startY = y; // default to stopY

                    if (i == 3) startY = 300;
                    if (i == 4) startY = 250;

                    barrels.add(new Barrel(new Point(x, y), shouldFall, startY));
                }
            }
        }

    }


    private void updateGameplay(Input input) {
        background.draw(Window.getWidth() / 2.0, Window.getHeight() / 2.0);

        for (Platform p : platforms) p.render();
        for (Ladder l : ladders) l.render();

        mario.update(input, platforms, ladders);

        donkey.update();
        hammer.update();

        mario.render();
        donkey.render();
        hammer.render();

        Iterator<Barrel> it = barrels.iterator();
        while (it.hasNext()) {
            Barrel barrel = it.next();
            barrel.update();
            barrel.render();
            scoreManager.checkAndAddBarrelJumped(mario, barrel);

            if (!mario.hasHammer() && GameUtils.isColliding(mario.getPosition(), 32, 32, barrel.getPosition(), barrel.getWidth(), barrel.getHeight())) {
                state = GameState.GAME_OVER;
                return;
            }

            if (mario.hasHammer() && GameUtils.isColliding(mario.getPosition(), 32, 32, barrel.getPosition(), barrel.getWidth(), barrel.getHeight())) {
                scoreManager.addBarrelDestroyed();
                it.remove();
                continue;
            }
        }

        if (!hammer.isCollected() && GameUtils.isColliding(mario.getPosition(), 32, 32, hammer.getPosition(), 32, 32)) {
            mario.collectHammer();
            hammer.collect();
        }

        if (GameUtils.isColliding(mario.getPosition(), 32, 32, donkey.getPosition(), donkey.getWidth(), donkey.getHeight())) {
            if (mario.hasHammer()) {
                scoreManager.calculateBonus((GameUtils.getIntProperty(gameProps, "gamePlay.maxFrames", 10000) - currentFrame) / 60);
                state = GameState.GAME_OVER;
            } else {
                state = GameState.GAME_OVER;
            }
            return;
        }

        if ((GameUtils.getIntProperty(gameProps, "gamePlay.maxFrames", 10000) - currentFrame) <= 0) {
            state = GameState.GAME_OVER;
            return;
        }

        drawHUD();
        currentFrame++;
    }

    private void drawHUD() {
        int scoreX = GameUtils.getIntProperty(gameProps, "gamePlay.score.x", 50);
        int scoreY = GameUtils.getIntProperty(gameProps, "gamePlay.score.y", 50);
        int maxFrames = GameUtils.getIntProperty(gameProps, "gamePlay.maxFrames", 10000);

        int remainingTime = (maxFrames - currentFrame) / 60;
        scoreFont.drawString("Score " + scoreManager.getFinalScore(), scoreX, scoreY);
        scoreFont.drawString("Time Left " + remainingTime, scoreX, scoreY + 30);
    }

    private void drawGameOverScreen() {
        background.draw(Window.getWidth() / 2.0, Window.getHeight() / 2.0);

        String status = mario.hasHammer() ? messageProps.getProperty("gameEnd.won") : messageProps.getProperty("gameEnd.lost");
        int centerX = Window.getWidth() / 2;

        Font statusFont = new Font(gameProps.getProperty("font"),
                GameUtils.getIntProperty(gameProps, "gameEnd.status.fontSize", 24));
        Font scoreFont = new Font(gameProps.getProperty("font"),
                GameUtils.getIntProperty(gameProps, "gameEnd.scores.fontSize", 20));

        statusFont.drawString(status,
                centerX - statusFont.getWidth(status) / 2,
                GameUtils.getIntProperty(gameProps, "gameEnd.status.y", 500));

        String scoreStr = messageProps.getProperty("gameEnd.score") + " " + scoreManager.getFinalScore();
        scoreFont.drawString(scoreStr,
                centerX - scoreFont.getWidth(scoreStr) / 2,
                GameUtils.getIntProperty(gameProps, "gameEnd.status.y", 500) + 60);

        String prompt = messageProps.getProperty("gameEnd.continue");
        scoreFont.drawString(prompt,
                centerX - scoreFont.getWidth(prompt) / 2,
                Window.getHeight() - 100);
    }


    public static void main(String[] args) {
        Properties gameProps = IOUtils.readPropertiesFile("res/app.properties");
        Properties messageProps = IOUtils.readPropertiesFile("res/message_en.properties");
        ShadowDonkeyKong game = new ShadowDonkeyKong(gameProps, messageProps);
        game.run();
    }
}


//Entity Interface
interface Entity {
    void update();

    void render();
}


//Game State
enum GameState {
    HOME,
    GAMEPLAY,
    GAME_OVER
}


//GameUtils Class
class GameUtils {
    public static boolean isColliding(Point pos1, double width1, double height1,
                                      Point pos2, double width2, double height2) {
        return pos1.x < pos2.x + width2 &&
                pos1.x + width1 > pos2.x &&
                pos1.y < pos2.y + height2 &&
                pos1.y + height1 > pos2.y;
    }

    public static int getIntProperty(Properties props, String key, int defaultValue) {
        try {
            return Integer.parseInt(props.getProperty(key));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public static double getDoubleProperty(Properties props, String key, double defaultValue) {
        try {
            return Double.parseDouble(props.getProperty(key));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}


//Hammer Class
class Hammer implements Entity {
    private final Image image;
    private Point position;
    private boolean collected = false;

    public Hammer(Properties props) {
        double x = GameUtils.getDoubleProperty(props, "hammer.x", 650);
        double y = GameUtils.getDoubleProperty(props, "hammer.y", 450);
        this.position = new Point(x, y);
        this.image = new Image("res/hammer.png");
    }

    @Override
    public void update() {
    }

    @Override
    public void render() {
        if (!collected) {
            image.drawFromTopLeft(position.x, position.y);
        }
    }

    public Point getPosition() {
        return position;
    }

    public boolean isCollected() {
        return collected;
    }

    public void reset(Properties props) {
        double x = GameUtils.getDoubleProperty(props, "hammer.x", 650);
        double y = GameUtils.getDoubleProperty(props, "hammer.y", 450);
        position = new Point(x, y);
        collected = false;
    }


    public void collect() {
        collected = true;
    }
}


//IOUtils Class
class IOUtils {

    public static Properties readPropertiesFile(String configFile) {
        Properties appProps = new Properties();
        try {
            appProps.load(new FileInputStream(configFile));
        } catch (IOException ex) {
            ex.printStackTrace();
            System.exit(-1);
        }

        return appProps;
    }

}


//Ladder Class
class Ladder implements Entity {
    private final Image image;
    private final Point position;
    private final double width;
    private final double height;

    public Ladder(Point position) {
        this.image = new Image("res/ladder.png");
        this.position = position;
        this.width = image.getWidth();
        this.height = image.getHeight();
    }

    @Override
    public void update() {
    }

    @Override
    public void render() {
        image.drawFromTopLeft(position.x, position.y);
    }

    public Point getPosition() {
        return position;
    }

    public double getWidth() {
        return width;
    }

    public double getHeight() {
        return height;
    }
}


//Platform Class
class Platform implements Entity {
    private final Image image;
    private final Point position;
    private final double width;
    private final double height;

    public Platform(Point position) {
        this.image = new Image("res/platform.png");
        this.position = position;
        this.width = image.getWidth();
        this.height = image.getHeight();
    }

    @Override
    public void update() {
    }

    @Override
    public void render() {
        image.drawFromTopLeft(position.x, position.y);
    }

    public Point getPosition() {
        return position;
    }

    public double getWidth() {
        return width;
    }

    public double getHeight() {
        return height;
    }
}


//Score Manager Class
class ScoreManager {
    private int barrelDestroyed;
    private int barrelJumped;
    private int bonusTime;
    private final Set<Barrel> jumpedBarrels;

    public ScoreManager() {
        jumpedBarrels = new HashSet<>();
    }

    public void addBarrelDestroyed() {
        barrelDestroyed++;
    }

    public void checkAndAddBarrelJumped(Mario mario, Barrel barrel) {
        if (!jumpedBarrels.contains(barrel)) {
            Point marioPos = mario.getPosition();
            Point barrelPos = barrel.getPosition();

            if (marioPos.y + 32 < barrelPos.y &&
                    Math.abs(marioPos.x - barrelPos.x) < 40) {
                barrelJumped++;
                jumpedBarrels.add(barrel);
            }
        }
    }

    public void calculateBonus(int remainingTimeSeconds) {
        bonusTime = remainingTimeSeconds * 3;
    }

    public int getFinalScore() {
        return barrelDestroyed * 100 + barrelJumped * 30 + bonusTime;
    }

    public int getBarrelDestroyed() {
        return barrelDestroyed;
    }

    public int getBarrelJumped() {
        return barrelJumped;
    }

    public int getBonusTime() {
        return bonusTime;
    }

    public void reset() {
        barrelDestroyed = 0;
        barrelJumped = 0;
        bonusTime = 0;
        jumpedBarrels.clear();
    }

    public void addBarrelJumped() {
    }
}


//Barrel Class
class Barrel implements Entity {
    private final Image image;
    private Point position;
    private final double width;
    private final double height;
    private final double stopY;
    private boolean falling;

    private double velocityY = 0;
    private final double GRAVITY = 0.5;

    public Barrel(Point stopPosition, boolean falling, double startY) {
        this.image = new Image("res/barrel.png");
        this.width = image.getWidth();
        this.height = image.getHeight();
        this.stopY = stopPosition.y;
        this.falling = falling;
        this.position = falling ? new Point(stopPosition.x, startY) : new Point(stopPosition.x, stopPosition.y);
    }

    @Override
    public void update() {
        if (falling && position.y < stopY) {
            velocityY += GRAVITY;
            position = new Point(position.x, position.y + velocityY);

            if (position.y >= stopY) {
                position = new Point(position.x, stopY);
                velocityY = 0;
                falling = false;
            }
        }
    }

    @Override
    public void render() {
        image.drawFromTopLeft(position.x, position.y);
    }

    public Point getPosition() {
        return position;
    }

    public double getWidth() {
        return width;
    }

    public double getHeight() {
        return height;
    }
}


// DonkeyKong Class
class DonkeyKong implements Entity {
    private final Image image;
    private Point position;
    private final double width;
    private final double height;

    private double targetY;
    private double velocityY = 0;
    private final double gravity = 0.2;
    private final double MAX_FALL_SPEED = 10;
    private boolean fallingIn = true;

    public DonkeyKong(Properties props) {
        double x = GameUtils.getDoubleProperty(props, "donkey.x", 100);
        targetY = GameUtils.getDoubleProperty(props, "donkey.y", 150);
        this.position = new Point(x, 0);
        this.image = new Image("res/donkey_kong.png");
        this.width = image.getWidth();
        this.height = image.getHeight();
    }

    @Override
    public void update() {
        if (fallingIn) {
            velocityY += gravity;
            if (velocityY > MAX_FALL_SPEED) {
                velocityY = MAX_FALL_SPEED;
            }

            double nextY = position.y + velocityY;

            if (nextY >= targetY) {
                nextY = targetY;
                fallingIn = false;
                velocityY = 0;
            }

            position = new Point(position.x, nextY);
        }
    }

    @Override
    public void render() {
        image.drawFromTopLeft(position.x, position.y);
    }

    public void reset(Properties props) {
        double x = GameUtils.getDoubleProperty(props, "donkey.x", 100);
        targetY = GameUtils.getDoubleProperty(props, "donkey.y", 150);
        position = new Point(x, 0);
        velocityY = 0;
        fallingIn = true;
    }

    public Point getPosition() {
        return position;
    }

    public double getWidth() {
        return width;
    }

    public double getHeight() {
        return height;
    }
}


// Mario Class
class Mario implements Entity {
    private Image leftImage;
    private Image rightImage;
    private Image hammerLeftImage;
    private Image hammerRightImage;

    private Point position;
    private double speed = 3.5;
    private double climbSpeed = 2;
    private double jumpVelocity = -5;
    private double gravity = 0.2;
    private double velocityY = 0;
    private boolean inAir = false;
    private boolean hasHammer = false;
    private boolean climbing = false;
    private boolean movingRight = true;
    private boolean onPlatform = false;
    private boolean fallingIn = true;
    private double targetY;

    private final double MAX_FALL_SPEED = 10;

    public Mario(Properties props) {
        double x = GameUtils.getDoubleProperty(props, "mario.start.x", 100);
        targetY = GameUtils.getDoubleProperty(props, "mario.start.y", 700);
        this.position = new Point(x, 0);

        leftImage = new Image("res/mario_left.png");
        rightImage = new Image("res/mario_right.png");
        hammerLeftImage = new Image("res/mario_hammer_left.png");
        hammerRightImage = new Image("res/mario_hammer_right.png");
    }

    public void update(Input input, List<Platform> platforms, List<Ladder> ladders) {
        double nextX = position.x;
        double nextY = position.y;
        climbing = false;

        if (fallingIn) {
            velocityY += gravity;
            nextY += velocityY;
            if (nextY >= targetY) {
                nextY = targetY;
                fallingIn = false;
                velocityY = 0;
            }
            position = new Point(position.x, nextY);
            return;
        }

        if (input.isDown(Keys.LEFT)) {
            nextX -= speed;
            movingRight = false;
        } else if (input.isDown(Keys.RIGHT)) {
            nextX += speed;
            movingRight = true;
        }

        for (Ladder ladder : ladders) {
            Point ladderPos = ladder.getPosition();
            boolean onLadder = GameUtils.isColliding(position, 32, 40, ladderPos, ladder.getWidth(), ladder.getHeight());

            Point downCheckPos = new Point(position.x, position.y + 5);
            boolean canClimbDown = GameUtils.isColliding(downCheckPos, 32, 40, ladderPos, ladder.getWidth(), ladder.getHeight());

            if (onLadder || (input.isDown(Keys.DOWN) && canClimbDown)) {
                if (input.isDown(Keys.UP)) {
                    nextY -= climbSpeed;
                    climbing = true;
                } else if (input.isDown(Keys.DOWN)) {
                    double ladderBottom = ladderPos.y + ladder.getHeight();
                    if (position.y + 40 < ladderBottom) {
                        nextY += climbSpeed;
                        if (nextY + 40 > ladderBottom) {
                            nextY = ladderBottom - 40;
                        }
                        climbing = true;
                    }
                }

                break;
            }
        }

        if (!climbing) {
            velocityY += gravity;
            if (velocityY > MAX_FALL_SPEED) {
                velocityY = MAX_FALL_SPEED;
            }
            nextY += velocityY;
        } else {
            velocityY = 0;
        }

        onPlatform = false;
        for (Platform p : platforms) {
            boolean standingOn = position.y + 40 <= p.getPosition().y &&
                    nextY + 40 >= p.getPosition().y &&
                    position.x + 32 > p.getPosition().x &&
                    position.x < p.getPosition().x + p.getWidth();

            if (standingOn) {
                onPlatform = true;
                if (!climbing) {
                    nextY = p.getPosition().y - 40;
                    inAir = false;
                    velocityY = 0;
                }
            }
        }

        if (input.wasPressed(Keys.SPACE) && onPlatform) {
            velocityY = jumpVelocity;
            inAir = true;
        }
        position = new Point(nextX, nextY);
    }

    @Override
    public void update() {
    }

    public void render() {
        Image toDraw;
        if (hasHammer) {
            toDraw = movingRight ? hammerRightImage : hammerLeftImage;
        } else {
            toDraw = movingRight ? rightImage : leftImage;
        }
        float renderOffsetY = hasHammer ? -20 : 0;
        toDraw.drawFromTopLeft(position.x, position.y + renderOffsetY);

    }

    public void reset(Properties props) {
        double x = GameUtils.getDoubleProperty(props, "mario.start.x", 100);
        targetY = GameUtils.getDoubleProperty(props, "mario.start.y", 700);
        position = new Point(x, 600);
        velocityY = 0;
        inAir = false;
        hasHammer = false;
        climbing = false;
        movingRight = true;
        fallingIn = true;
    }

    public void collectHammer() {
        hasHammer = true;
    }

    public boolean hasHammer() {
        return hasHammer;
    }

    public Point getPosition() {
        return position;
    }
}
