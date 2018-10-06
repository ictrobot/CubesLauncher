package ethanjones.cubes.launcher;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Map.Entry;

public class VersionWindow extends JFrame {
  static VersionWindow INSTANCE;
  private static Comparator<String> stringComparator = Collections.reverseOrder(String.CASE_INSENSITIVE_ORDER);
  
  private ImageIcon image;
  private JLabel imageLabel;
  private JLabel loadingLabel;
  
  private Box box;
  private JComboBox<String> version;
  private JButton play;

  private RunStatus runStatus;
  private JLabel runLabel;
  private JProgressBar runProgress;

  
  VersionWindow() {
    INSTANCE = this;
    
    setLayout(new BorderLayout());
    setSize(240, 140);
    
    if (new File("logo.png").exists()) {
      image = new ImageIcon("logo.png");
    } else {
      image = new ImageIcon(getClass().getResource("/logo.png"));
    }
    imageLabel = new JLabel(image);
    add(imageLabel, BorderLayout.CENTER);
    
    loadingLabel = new JLabel("Loading", SwingConstants.CENTER);
    add(loadingLabel, BorderLayout.PAGE_END);
    
    addWindowListener(new WindowAdapter() {
      public void windowClosing(WindowEvent we) {
        dispose();
      }
    });
    
    setVisible(true);
    setLocationRelativeTo(null);
  }
  
  void setVersions() {
    if (box != null) {
      remove(box);
      box = null;
    }
    if (loadingLabel != null) {
      remove(loadingLabel);
      loadingLabel = null;
    }
    
    ArrayList<String> releases = new ArrayList<String>();
    ArrayList<String> snapshots = new ArrayList<String>();
    for (Entry<String, Version> entry : CubesLauncher.versions.entrySet()) {
      if (entry.getValue().release) {
        releases.add(entry.getKey());
      } else {
        snapshots.add(entry.getKey());
      }
    }
    Collections.sort(releases, stringComparator);
    Collections.sort(snapshots, stringComparator);
    ArrayList<String> versions = new ArrayList<String>();
    versions.addAll(releases);
    versions.addAll(snapshots);

    System.out.println("\nVersions:");
    for (String version : versions) {
      System.out.println(CubesLauncher.versions.get(version).toString());
    }
    System.out.println();

    String[] array = versions.toArray(new String[versions.size()]);
    
    box = Box.createHorizontalBox();
    
    version = new JComboBox<String>(array);
    box.add(version);
    box.add(Box.createHorizontalGlue());
    play = new JButton("Play");
    play.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        final String s = (String) version.getSelectedItem();
        final RunStatus runStatus = new RunStatus();
        VersionWindow.this.runStatus = runStatus;
        new Thread("Cubes-Run") {
          @Override
          public void run() {
            CubesLauncher.run(CubesLauncher.versions.get(s), runStatus);
          }
        }.start();
      }
    });
    box.add(play);
    add(box, BorderLayout.PAGE_END);
    revalidate();
  }

  void updateRunProgress() {
    if (runStatus != null) {
      if (box != null) {
        remove(box);
        box = null;
      }
      if (loadingLabel != null) {
        remove(loadingLabel);
        loadingLabel = null;
      }

      if (runStatus.stage == RunStatus.Stage.verifying) {
        if (runProgress != null) {
          remove(runProgress);
          runProgress = null;
        }

        if (runLabel == null) {
          runLabel = new JLabel("Verifying", SwingConstants.CENTER);
          add(runLabel, BorderLayout.PAGE_END);
        }
      } else if (runStatus.stage == RunStatus.Stage.downloading) {
        if (runLabel != null) {
          remove(runLabel);
          runLabel = null;
        }

        if (runProgress == null) {
          runProgress = new JProgressBar(0, 100);
          runProgress.setStringPainted(true);
          add(runProgress, BorderLayout.PAGE_END);
        }
        int percent = (int) (runStatus.progress * 100);
        runProgress.setValue(percent);
        runProgress.setString("Downloading " + percent +"%");
      }
    }
  }
}
