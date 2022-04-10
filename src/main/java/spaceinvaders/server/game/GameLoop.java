package spaceinvaders.server.game;

import static spaceinvaders.game.EntityEnum.INVADER;
import static spaceinvaders.game.EntityEnum.INVADER_BULLET;
import static spaceinvaders.game.EntityEnum.PLAYER;
import static spaceinvaders.game.EntityEnum.PLAYER_BULLET;
import static spaceinvaders.game.EntityEnum.SHIELD;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import spaceinvaders.command.Command;
import spaceinvaders.command.client.ChangeScoreCommand;
import spaceinvaders.command.client.GameOverCommand;
import spaceinvaders.command.client.MoveEntityCommand;
import spaceinvaders.command.client.SpawnEntityCommand;
import spaceinvaders.command.client.TranslateGroupCommand;
import spaceinvaders.command.client.WipeOutEntityCommand;
import spaceinvaders.game.EntityEnum;
import spaceinvaders.game.GameConfig;
import spaceinvaders.server.game.world.LogicEntity;
import spaceinvaders.server.game.world.PlayerBullet;
import spaceinvaders.server.game.world.World;
import spaceinvaders.server.player.Player;
import spaceinvaders.utility.AutoSwitch;
import spaceinvaders.utility.Service;

/** Provides methods for handling player input and advancing the game simulation. */
public class GameLoop implements Service<Void> {
  private static final int GUARD_PIXELS = 32;

  private final GameConfig config = GameConfig.getInstance();
  private final AutoSwitch invadersMovement = new AutoSwitch(config.speed().invader().getRate()); 
  private final AutoSwitch bulletsMovement = new AutoSwitch(config.speed().bullet().getRate()); 
  private final AutoSwitch invadersShooting = new AutoSwitch(); 
  private final List<Future<?>> future = new ArrayList<>();
  private final List<Command> commandBuf = new ArrayList<>();
  private final List<Player> team;
  private final World world;
  private final Random rng;
  private final ExecutorService threadPool;
  private final Integer invadersVelocityY = config.speed().invader().getDistance() * 2;
  private Integer invadersVelocityX = config.speed().invader().getDistance();
  private boolean gameOver = false;
  private ArrayList<Integer> lastCommand ;

  /**
   * @param team human players.
   * @param world game environment.
   * @param rng pseudorandom number generator.
   * @param threadPool used for running auto-switches.
   *
   * @throws NullPointerException if an argument is {@code null}.
   */
  public GameLoop(List<Player> team, World world, Random rng, ExecutorService threadPool) {
    if (team == null || world == null || rng == null || threadPool == null) {
      throw new NullPointerException();
    }
    this.team = team;
    this.world = world;
    this.rng = rng;
    this.threadPool = threadPool;
    invadersShooting.setRate(world.count(INVADER) * config.getInvadersShootingFactor());
    this.lastCommand = new ArrayList<>();
    for(int i=0;i<team.size();i++){
      lastCommand.add(0);
    }
  }

  /**
   * Start auto-switches.
   *
   * @throws RejectedExecutionException if a subtask cannot be executed.
   */
  @Override
  public Void call() {
    future.add(threadPool.submit(invadersMovement));
    future.add(threadPool.submit(bulletsMovement));
    future.add(threadPool.submit(invadersShooting));
    return null;
  }

  @Override
  public void shutdown() {
    invadersMovement.shutdown();
    bulletsMovement.shutdown();
    invadersShooting.shutdown();
    for (Future<?> it : future) {
      it.cancel(true);
    }
  }

  /** Drain the command buffer. */
  public Command[] drainCommands() {
    Command[] commands = new Command[commandBuf.size()];
    commandBuf.toArray(commands);
    commandBuf.clear();
    return commands;
  }

  /** Handle user input that has happened since the last call. */
  public void processInput() {
    //execute lastCommand as the prediction of current command
    Iterator<Player> it = team.iterator();
    Player player;
    int p=0;
    while (it.hasNext()) {
      player = it.next();
      int id = player.getId();
      ExecutorService ex = Executors.newSingleThreadExecutor();
      int finalP = p;
      ex.submit(new Callable<Object>() {
        @Override
        public Object call() throws Exception {
          if (lastCommand.get(finalP) > 0) {//player has turned left
            movePlayerLeft(id);
            System.out.println("turn left");
          } else if (lastCommand.get(finalP) < 0) {//player has turned right
            movePlayerRight(id);
            System.out.println("turn right");
          }
          // if player has done nothing since last call, then do nothing as well
          return null;
        }
      });
      p++;
    }
    // mark the original position
    Iterator<LogicEntity> it1 = world.getIterator(PLAYER);
    ArrayList<Integer>pos = new ArrayList<>();
    while(it1.hasNext()){
      LogicEntity it_player = it1.next();
      pos.add(it_player.getX());
    }
    // original code here
    it = team.iterator();
    while (it.hasNext()) {
      player = it.next();
      if (player.isOnline()) {
        List<Command> commands = player.pull();
        for (Command command : commands) {
          command.setExecutor(this);
          command.execute();
        }
      } else {
        player.close();
        it.remove();
        Iterator<LogicEntity> entityIt = world.getIterator(PLAYER);
        while (entityIt.hasNext()) {
          if (entityIt.next().getId() == player.getId()) {
            entityIt.remove();
            break;
          }
        }
        commandBuf.add(new WipeOutEntityCommand(player.getId()));
      }
    }
    //get the player's position, judge if it's legal, and if it is not, print out a message
    it1 = world.getIterator(PLAYER);
    ArrayList<Integer>newPos = new ArrayList<>();
    while (it1.hasNext()) {
      LogicEntity it_player = it1.next();
      newPos.add(it_player.getX());
    }
    it = team.iterator();
    for(int i=0;i<pos.size();i++){
      player = it.next();
      if(Math.abs(pos.get(i)-newPos.get(i))> 5 * config.speed().player().getDistance()){
        System.out.println("CHEAT DETECTED!");
      }
      //Since I do not know the player's specific command to the server, I check the position to find out what command it is.
      int curCommand = pos.get(i)-newPos.get(i);
      //handle the different cases
      int lc = lastCommand.get(i);
      if(curCommand>0){
        if(lc>0){
          //The two steps are the same, we want to move left 2 times, but we already move left 4 times, so we move back 2 times
          for(int k=0;k<2;k++) {
            movePlayerRight(player.getId());
          }
        }else if(lc<0){
          // last step we go left, but now we want to go right, so actually we stay the original place, we need to do nothing
        }
        else if(lc==0){
          // last step we go left, now we do nothing, so we move back 1 time
          movePlayerRight(player.getId());
        }
      }else if(curCommand<0){
        // The logic is the same with the above logic
        if(lc<0){
          for(int k=0;k<2;k++) {
            movePlayerLeft(player.getId());
          }
        }else if(lc==0){
          movePlayerLeft(player.getId());
        }
      }

      lastCommand.set(i,curCommand);
    }

  }

  /**
   * Move a player one step to the left.
   *
   * @param id the ID of the player.
   */
  public void movePlayerLeft(int id) {
    Iterator<LogicEntity> it = world.getIterator(PLAYER);
    while (it.hasNext()) {
      LogicEntity player = it.next();
      if (player.getId() == id) {
        movePlayer(player,player.getX() - config.speed().player().getDistance());
      }
    }
  }

  /**
   * Move a player one step to the right.
   *
   * @param id the ID of the player.
   */
  public void movePlayerRight(int id) {
    Iterator<LogicEntity> it = world.getIterator(EntityEnum.PLAYER);
    while (it.hasNext()) {
      LogicEntity player = it.next();
      if (player.getId() == id) {
        movePlayer(player,player.getX() + config.speed().player().getDistance());
      }
    }
  }

  private void movePlayer(LogicEntity player, int newX) {
    final int playerW = config.player().getWidth();
    final int frameW = config.frame().getWidth();
    if (newX >= GUARD_PIXELS && newX <= frameW - playerW - GUARD_PIXELS) {
      moveEntity(player,newX,player.getY());
    }
  }

  private void moveEntity(LogicEntity entity, int newX, int newY) {
    entity.move(newX,newY);
    commandBuf.add(
        new MoveEntityCommand(entity.getId(),newX,newY));
  }

  /**
   * A player shoots a bullet.
   *
   * @param id the ID of the shooter.
   */
  public void playerShoot(int id) {
    Iterator<LogicEntity> it = world.getIterator(EntityEnum.PLAYER);
    while (it.hasNext()) {
      LogicEntity player = it.next();
      if (player.getId() == id) {
        final int playerW = config.player().getWidth();
        final int bulletW = config.playerBullet().getWidth();
        final int bulletX = player.getX() + playerW / 2 - bulletW / 2;
        final int bulletY = player.getY() - GUARD_PIXELS;
        LogicEntity bullet = world.spawnPlayerBullet(player.getId(),bulletX,bulletY);
        commandBuf.add(
            new SpawnEntityCommand(bullet.getId(),PLAYER_BULLET,bullet.getX(),bullet.getY()));
      }
    }
  }

  /** Advance the game simulation one step. */
  public void update() {
    entitiesMove();
    entitiesShoot();
    detectCollision();
  }

  /** Move all entities controlled by the CPU. */
  private void entitiesMove() {
    if (gameOver) {
      return;
    }

    Iterator<LogicEntity> it;

    /* Move invaders */
    if (invadersMovement.isOn()) {
      boolean moveDown = false;
      if (invadersVelocityX > 0) {
        int maxX = Integer.MIN_VALUE;
        it = world.getIterator(INVADER);
        while (it.hasNext()) {
          LogicEntity invader = it.next();
          maxX = Math.max(maxX,invader.getX());
        }
        if (maxX + config.invader().getWidth() >= config.frame().getWidth() - GUARD_PIXELS) {
          moveDown = true;
        }
      } else {
        int minX = Integer.MAX_VALUE;
        it = world.getIterator(INVADER);
        while (it.hasNext()) {
          LogicEntity invader = it.next();
          minX = Math.min(minX,invader.getX());
        }
        if (minX <= GUARD_PIXELS) {
          moveDown = true;
        }
      }
      if (moveDown) {
        // Change horizontal direction.
        invadersVelocityX = -invadersVelocityX;
        commandBuf.add(new TranslateGroupCommand(INVADER,0,invadersVelocityY));
        int maxY = Integer.MIN_VALUE;
        it = world.getIterator(INVADER);
        while (it.hasNext()) {
          LogicEntity invader = it.next();
          invader.move(invader.getX(),invader.getY() + invadersVelocityY);
          maxY = Math.max(maxY,invader.getY());
        }
        if (maxY >= config.frame().getHeight() - config.player().getHeight()) {
          /* Invaders reached players. */
          it = world.getIterator(PLAYER);
          while (it.hasNext()) {
            it.next();
            it.remove();
          }
          gameOver = true;
          return;
        }
      } else {
        /* Invaders move horizontally. */
        it = world.getIterator(INVADER);
        LogicEntity invader = null;
        while (it.hasNext()) {
          invader = it.next();
          invader.move(invader.getX() + invadersVelocityX,invader.getY());
        }
        if (invader == null) {
          // This should never happen.
          throw new AssertionError();
        }
        commandBuf.add(new TranslateGroupCommand(INVADER,invadersVelocityX,0));
      }
      invadersMovement.toggle();
    }

    /* Move bullets. */
    if (bulletsMovement.isOn()) {
      int distance = config.speed().bullet().getDistance();

      /* Invader bullets */
      if (world.count(INVADER_BULLET) > 0) {
        it = world.getIterator(INVADER_BULLET);
        while (it.hasNext()) {
          LogicEntity bullet = it.next();
          bullet.move(bullet.getX(),bullet.getY() + distance);
          if (bullet.getY() >= config.frame().getHeight() + config.frame().getHeight() / 4) {
            /* Dirty trick to make sure the bullet disappears. */
            it.remove();
          }
        }
        commandBuf.add(new TranslateGroupCommand(INVADER_BULLET,0,distance));
      }

      /* Player bullets */
      distance = -distance;
      if (world.count(PLAYER_BULLET) > 0) {
        it = world.getIterator(PLAYER_BULLET);
        while (it.hasNext()) {
          LogicEntity bullet = it.next();
          bullet.move(bullet.getX(),bullet.getY() + distance);
          if (bullet.getY() + config.playerBullet().getHeight() < 0) {
            commandBuf.add(new ChangeScoreCommand(((PlayerBullet) bullet).getShooterId(),-1));
            it.remove();
          }
        }
        commandBuf.add(new TranslateGroupCommand(PLAYER_BULLET,0,distance));
      }

      bulletsMovement.toggle();
    }
  }

  private void entitiesShoot() {
    if (gameOver) {
      return;
    }

    Iterator<LogicEntity> it;

    /* Invaders shoot. */
    if (invadersShooting.isOn()) {
      /* Get the lowest invader in each column. */
      List<LogicEntity> shooters = new ArrayList<LogicEntity>(config.getInvaderCols());
      it = world.getIterator(INVADER);
      while (it.hasNext()) {
        LogicEntity invader = it.next();
        LogicEntity shooter;
        boolean addToEnd = true;
        for (int index = 0; index < shooters.size(); ++index) {
          shooter = shooters.get(index);
          if (shooter.getX() == invader.getX()) {
            if (invader.getY() > shooter.getY()) {
              shooters.set(index,invader);
            }
            addToEnd = false;
            break;
          }
        }
        if (addToEnd) {
          shooters.add(invader);
        }
      }

      LogicEntity shooter = shooters.get(rng.nextInt(shooters.size()));
      int bulletX = shooter.getX() + config.invader().getWidth() / 2
          - config.invaderBullet().getWidth() / 2;
      int bulletY = shooter.getY() + config.invader().getHeight() + 5;
      LogicEntity bullet = world.spawnInvaderBullet(bulletX,bulletY);
      commandBuf.add(
          new SpawnEntityCommand(bullet.getId(),INVADER_BULLET,bullet.getX(),bullet.getY()));
      invadersShooting.toggle();
    }
  }

  private void detectCollision() {
    if (gameOver) {
      return;
    }

    Iterator<LogicEntity> invaderIt;
    Iterator<LogicEntity> playerIt;
    Iterator<LogicEntity> shieldIt;
    Iterator<LogicEntity> playerBulletIt;
    Iterator<LogicEntity> invaderBulletIt;

    /* Invaders vs shields. */
    invaderIt = world.getIterator(INVADER);
    while (invaderIt.hasNext()) {
      LogicEntity invader = invaderIt.next();
      shieldIt = world.getIterator(SHIELD);
      LogicEntity shield;
      while (shieldIt.hasNext()) {
        shield = shieldIt.next();
        if (shield.collides(invader)) {
          commandBuf.add(new WipeOutEntityCommand(shield.getId()));
          shieldIt.remove();
        }
      }
    }

    /* Player bullets vs invader bullets. */
    playerBulletIt = world.getIterator(PLAYER_BULLET);
    while (playerBulletIt.hasNext()) {
      LogicEntity playerBullet = playerBulletIt.next();
      invaderBulletIt = world.getIterator(INVADER_BULLET);
      LogicEntity invaderBullet;
      while (invaderBulletIt.hasNext()) {
        invaderBullet = invaderBulletIt.next();
        if (playerBullet.collides(invaderBullet)) {
          commandBuf.add(new WipeOutEntityCommand(invaderBullet.getId()));
          commandBuf.add(new WipeOutEntityCommand(playerBullet.getId()));
          invaderBulletIt.remove();
          playerBulletIt.remove();
          break;
        }
      }
    }

    /* Player bullets vs invaders. */
    playerBulletIt = world.getIterator(PLAYER_BULLET);
    while (playerBulletIt.hasNext()) {
      LogicEntity playerBullet = playerBulletIt.next();
      invaderIt = world.getIterator(INVADER);
      while (invaderIt.hasNext()) {
        LogicEntity invader = invaderIt.next();
        if (playerBullet.collides(invader)) {
          commandBuf.add(new WipeOutEntityCommand(invader.getId()));
          commandBuf.add(new WipeOutEntityCommand(playerBullet.getId()));
          commandBuf.add(new ChangeScoreCommand(((PlayerBullet) playerBullet).getShooterId(),2));
          invaderIt.remove();
          playerBulletIt.remove();

          /* Speed things up. */
          invadersShooting.setRate(world.count(INVADER) * config.getInvadersShootingFactor());
          break;
        }
      }
    }

    /* Invader bullets vs shields. */
    invaderBulletIt = world.getIterator(INVADER_BULLET);
    while (invaderBulletIt.hasNext()) {
      LogicEntity invaderBullet = invaderBulletIt.next();
      shieldIt = world.getIterator(SHIELD);
      LogicEntity shield;
      while (shieldIt.hasNext()) {
        shield = shieldIt.next();
        if (invaderBullet.collides(shield)) {
          commandBuf.add(new WipeOutEntityCommand(shield.getId()));
          commandBuf.add(new WipeOutEntityCommand(invaderBullet.getId()));
          invaderBulletIt.remove();
          shieldIt.remove();
          break;
        }
      }
    }

    /* Invader bullets vs players. */
    invaderBulletIt = world.getIterator(INVADER_BULLET);
    while (invaderBulletIt.hasNext()) {
      LogicEntity invaderBullet = invaderBulletIt.next();
      playerIt = world.getIterator(PLAYER);
      LogicEntity player;
      while (playerIt.hasNext()) {
        player = playerIt.next();
        if (invaderBullet.collides(player)) {
          commandBuf.add(new WipeOutEntityCommand(invaderBullet.getId()));
          commandBuf.add(new WipeOutEntityCommand(player.getId()));
          commandBuf.add(new GameOverCommand(player.getId()));
          invaderBulletIt.remove();
          playerIt.remove();
          break;
        }
      }
    }
  }
}
