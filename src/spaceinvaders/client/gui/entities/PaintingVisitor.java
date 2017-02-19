package spaceinvaders.client.gui.entities;

import java.awt.Graphics;
import java.awt.image.ImageObserver;
import spaceinvaders.client.gui.entities.Invader;
import spaceinvaders.client.gui.entities.InvaderBullet;
import spaceinvaders.client.gui.entities.Player;
import spaceinvaders.client.gui.entities.PlayerBullet;
import spaceinvaders.client.gui.entities.Shield;
import spaceinvaders.game.GameConfig;

/** Paints a GraphicalEntity on the screen. */
public class PaintingVisitor implements GraphicalEntityVisitor {
  private final GameConfig config = GameConfig.getInstance();
  private Graphics graphics;
  private ImageObserver imgObserver;

  /**
   * @param graphics - graphics context.
   * @param imgObserver - update interface for notifications about drawn images.
   *
   * @throws NullPointerException - if an argument is {@code null}.
   */
  public PaintingVisitor(Graphics graphics, ImageObserver imgObserver) {
    if (graphics == null || imgObserver == null) {
      throw new NullPointerException();
    }
    this.graphics = graphics;
    this.imgObserver = imgObserver;
  }

  /**
   * @throws NullPointerException - if the graphics context or the image observer are {@code null}.
   */
  @Override
  public void visit(Invader invader) {
    if (graphics == null || imgObserver == null) {
      throw new NullPointerException();
    }   
    graphics.drawImage(invader.getAvatar(invader.getAvatarNumber()),invader.getX(),invader.getY(),
        config.invader().getWidth(),config.invader().getHeight(),imgObserver);
  }

  /**
   * @throws NullPointerException - if the graphics context or the image observer are {@code null}.
   */
  @Override
  public void visit(InvaderBullet invaderBullet) {
    if (graphics == null || imgObserver == null) {
      throw new NullPointerException();
    }   
    graphics.drawImage(invaderBullet.getAvatar(invaderBullet.getAvatarNumber()),
        invaderBullet.getX(),invaderBullet.getY(),
        config.invaderBullet().getWidth(),config.invaderBullet().getHeight(),imgObserver);
  }

  /**
   * @throws NullPointerException - if the graphics context or the image observer are {@code null}.
   */
  @Override
  public void visit(Player player) {
    if (graphics == null || imgObserver == null) {
      throw new NullPointerException();
    }   
    graphics.drawImage(player.getAvatar(player.getAvatarNumber()),player.getX(),player.getY(),
        config.player().getWidth(),config.player().getHeight(),imgObserver);
    graphics.drawString(player.getName(),player.getX(),player.getY() + config.player().getHeight()
        + config.player().getHeight() / 4);
  }

  /**
   * @throws NullPointerException - if the graphics context or the image observer are {@code null}.
   */
  @Override
  public void visit(PlayerBullet playerBullet) {
    if (graphics == null || imgObserver == null) {
      throw new NullPointerException();
    }   
    graphics.drawImage(playerBullet.getAvatar(playerBullet.getAvatarNumber()),
        playerBullet.getX(),playerBullet.getY(),
        config.playerBullet().getWidth(),config.playerBullet().getHeight(),imgObserver);
  }

  /**
   * @throws NullPointerException - if the graphics context or the image observer are {@code null}.
   */
  @Override
  public void visit(Shield shield) {
    if (graphics == null || imgObserver == null) {
      throw new NullPointerException();
    }
    graphics.drawImage(shield.getAvatar(shield.getAvatarNumber()),shield.getX(),shield.getY(),
        config.shield().getWidth(),config.shield().getHeight(),imgObserver);
  }
}