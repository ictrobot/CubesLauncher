package ethanjones.cubes.launcher;

import javax.swing.JOptionPane;

public class UncaughtExceptionHandler implements Thread.UncaughtExceptionHandler {
  public static final UncaughtExceptionHandler INSTANCE = new UncaughtExceptionHandler();

  private UncaughtExceptionHandler() {

  }

  @Override
  public void uncaughtException(Thread t, Throwable e) {
    JOptionPane.showMessageDialog(null, e.toString(), "Cubes Launcher Error", JOptionPane.ERROR_MESSAGE);
  }
}
