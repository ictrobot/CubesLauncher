package ethanjones.cubes.launcher;

import javax.swing.*;

public class RunStatus {

  volatile Stage stage;
  volatile float progress;

  enum Stage {
    verifying,
    downloading
  }

  void update(Stage stage, float progress) {
    this.stage = stage;
    this.progress = progress;
    SwingUtilities.invokeLater(new Runnable() {
      @Override
      public void run() {
        VersionWindow.INSTANCE.updateRunProgress();
        VersionWindow.INSTANCE.validate();
        VersionWindow.INSTANCE.repaint();
      }
    });
  }

}
