package spaceinvaders.client.mvc;

import java.util.List;
import java.util.Observer;
import spaceinvaders.game.Entity;
import spaceinvaders.utility.Couple;

/**
 * Separates application data and user interface.
 *
 * @see spaceinvaders.client.mvc.Model
 * @see spaceinvaders.client.mvc.View
 */
public interface Controller extends Observer {
  /**
   * Register a view with this controller. 
   *
   * @param view the view to be registered
   */
  public void registerView(View view);

  /**
   * Return the only model registered with this controller.
   */
  public Model getModel(); 

  /**
   * Return a list of all views registered with this controller.
   */
  public List<View> getViews();
}