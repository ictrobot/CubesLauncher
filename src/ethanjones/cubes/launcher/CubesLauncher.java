package ethanjones.cubes.launcher;

import org.apache.commons.net.ftp.*;
import org.apache.commons.net.io.CopyStreamAdapter;

import javax.swing.*;
import java.io.*;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;

public class CubesLauncher {
  public static final String FTP_ADDRESS = "cubes.ethanjones.me";
  public static final String FTP_RELEASES_PATH = "maven/releases/ethanjones/cubes/client/";
  public static final String FTP_SNAPSHOTS_PATH = "maven/snapshots/ethanjones/cubes/client/";
  public static final String JAVA_CLASS = "ethanjones.cubes.core.platform.desktop.ClientLauncher";
  public static final HashMap<String, Version> versions = new HashMap<String, Version>();

  public static void main(String[] args) {
    Thread.currentThread().setUncaughtExceptionHandler(UncaughtExceptionHandler.INSTANCE);
    Thread.setDefaultUncaughtExceptionHandler(UncaughtExceptionHandler.INSTANCE);

    SwingUtilities.invokeLater(new Runnable() {
      @Override
      public void run() {
        Thread.currentThread().setUncaughtExceptionHandler(UncaughtExceptionHandler.INSTANCE);
        new VersionWindow();
      }
    });
    downloadVersions();
    System.out.println(versions.toString());
    SwingUtilities.invokeLater(new Runnable() {
      @Override
      public void run() {
        VersionWindow.INSTANCE.setVersions();
      }
    });
  }
  
  private static void downloadVersions() {
    FTPClient ftp = new FTPClient();
    FTPClientConfig config = new FTPClientConfig();
    ftp.configure(config);
    boolean error = false;
    try {
      int reply;
      ftp.connect(FTP_ADDRESS);
      System.out.println(ftp.getReplyString());
      
      if (!FTPReply.isPositiveCompletion(reply = ftp.getReplyCode())) {
        ftp.disconnect();
        throw new LauncherException("Failed to connect to ftp server " + reply);
      }
      
      ftp.user("ftp");
      if (!FTPReply.isPositiveCompletion(reply = ftp.getReplyCode())) {
        ftp.disconnect();
        throw new LauncherException("Failed to set ftp user " + reply);
      }
      
      FTPFile[] releasesDirectories = ftp.listDirectories(FTP_RELEASES_PATH);
      for (FTPFile ftpDirectory : releasesDirectories) {
        String v = ftpDirectory.getName();
        String path = FTP_RELEASES_PATH + v + "/client-" + v + ".jar";
        FTPFile[] ftpFiles = ftp.listFiles(path);
        if (ftpFiles == null || ftpFiles.length != 1) {
          System.out.println("Invalid release " + v + " " + path);
          continue;
        }
        versions.put(v, new Version(v, true, path, ftpFiles[0].getSize()));
      }
      
      FTPFile[] snapshotsDirectories = ftp.listDirectories(FTP_SNAPSHOTS_PATH);
      for (FTPFile ftpDirectory : snapshotsDirectories) {
        String v = ftpDirectory.getName();
        FTPFile[] ftpFiles = ftp.listFiles(FTP_SNAPSHOTS_PATH + v, new FTPFileFilter() {
          @Override
          public boolean accept(FTPFile file) {
            return file.getName().endsWith(".jar");
          }
        });
        Arrays.sort(ftpFiles, new Comparator<FTPFile>() {
          @Override
          public int compare(FTPFile o1, FTPFile o2) {
            return o1.getName().compareTo(o2.getName());
          }
        });
        FTPFile file = ftpFiles[ftpFiles.length - 1];
        String path = FTP_SNAPSHOTS_PATH + v + "/" + file.getName();
        versions.put(v, new Version(v, false, path, file.getSize()));
      }
      
      for (Version version : versions.values()) {
        InputStream inputStream = null;
        try {
          inputStream = ftp.retrieveFileStream(version.downloadPath + ".sha1");
          byte[] bytes = new byte[40];
          int bytesRead = 0;
          while (bytesRead != -1) {
            bytesRead = inputStream.read(bytes, bytesRead, bytes.length - bytesRead);
          }
          boolean success = ftp.completePendingCommand();
          inputStream.close();
          if (success) {
            String stringHash = new String(bytes, "ASCII");
            byte[] hash = hexStringToByteArray(stringHash);
            version.setSHA1Hash(stringHash, hash);
          } else {
            throw new LauncherException("Failed to download hash ");
          }
        } finally {
          if (inputStream != null) {
            try {
              inputStream.close();
            } catch (IOException ignored) {
            }
          }
        }
      }

      ftp.logout();
    } catch (IOException e) {
      error = true;
      e.printStackTrace();
    } finally {
      if (ftp.isConnected()) {
        try {
          ftp.disconnect();
        } catch (IOException ignored) {
          
        }
      }
    }
    if (error) {
      if (loadVersionCache()) {
        JOptionPane.showMessageDialog(null, "Error whilst downloading versions, using cached versions.", "Cubes Launcher", JOptionPane.INFORMATION_MESSAGE);
      } else {
        throw new LauncherException("Error whilst downloading versions, and no cache is available");
      }
    } else {
      saveVersionCache();
    }
  }

  private static void saveVersionCache() {
    long t = System.nanoTime();
    try {
      File launcherFolder = getLauncherFolder();
      File versionCache = new File(launcherFolder, "version_cache");
      ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(versionCache));
      oos.writeObject(versions);
      oos.close();
    } catch (Exception e) {
      System.out.println("Failed to save version cache");
      e.printStackTrace();
    }
    t = System.nanoTime() - t;
    System.out.println("Took " + t + "ns to save version cache");
  }

  private static boolean loadVersionCache() {
    try {
      versions.clear();
      File launcherFolder = getLauncherFolder();
      File versionCache = new File(launcherFolder, "version_cache");
      ObjectInputStream oos = new ObjectInputStream(new FileInputStream(versionCache));
      versions.putAll((HashMap<String, Version>) oos.readObject());
      oos.close();
      return true;
    } catch (Exception e) {
      System.out.println("Failed to load version cache");
      e.printStackTrace();
    }
    return false;
  }
  
  static void run(Version version, RunStatus runStatus) {
    File launcherFolder = getLauncherFolder();
    File versionFolder = new File(launcherFolder, "versions");
    versionFolder.mkdirs();
    File jarFile = new File(versionFolder, version.name + ".jar");

    int counter = 0;
    while (!verify(version, jarFile, runStatus)) {
      if (counter == 3) throw new IllegalStateException("Failed verification 3 times");
      counter++;

      downloadVersion(version, jarFile, runStatus);
    }

    SwingUtilities.invokeLater(new Runnable() {
      @Override
      public void run() {
        VersionWindow.INSTANCE.dispose();
      }
    });

    try {
      URLClassLoader classLoader = (URLClassLoader)ClassLoader.getSystemClassLoader();
      Method method = URLClassLoader.class.getDeclaredMethod("addURL", URL.class);
      method.setAccessible(true);
      method.invoke(classLoader, jarFile.toURI().toURL());
      method.setAccessible(false);
      
      Class c = Class.forName(JAVA_CLASS);
      final Method main = c.getDeclaredMethod("main", String[].class);
      new Thread() {
        @Override
        public void run() {
          try {
            main.invoke(null, (Object) new String[0]);
          } catch (Exception e) {
            throw new LauncherException("Failed to start client", e);
          }
        }
      }.run();
    } catch (Exception e) {
      throw new LauncherException("Failed to start client", e);
    }
  }

  private static boolean verify(Version version, File jarFile, RunStatus runStatus) {
    if (!jarFile.exists()) return false;
    if (runStatus != null) runStatus.update(RunStatus.Stage.verifying, 0);

    if (jarFile.length() == version.getExpectedFileSize()) {
      System.out.println(jarFile + "\t matches expected file size: " + version.getExpectedFileSize());
    } else {
      System.out.println(jarFile + "\t does not match expected file size: " + version.getExpectedFileSize());
      jarFile.delete();
      return false;
    }
    byte[] fileHash = sha1HashFile(jarFile);
    if (Arrays.equals(fileHash, version.getExpectedHash())) {
      System.out.println(jarFile + "\t matches expected hash: " + version.getExpectedHashString());
    } else {
      System.out.println(jarFile + "\t does not match expected hash: " + version.getExpectedHashString());
      jarFile.delete();
      return false;
    }
    return true;
  }
  
  private static void downloadVersion(final Version version, File local, final RunStatus runStatus) {
    if (version == null || version.downloadPath == null) throw new IllegalStateException("Version null");
    if (runStatus != null) runStatus.update(RunStatus.Stage.downloading, 0);

    FTPClient ftp = new FTPClient();
    FTPClientConfig config = new FTPClientConfig();
    ftp.configure(config);
    boolean error = false;
    try {
      int reply;
      ftp.connect(FTP_ADDRESS);
      System.out.println(ftp.getReplyString());
      
      if (!FTPReply.isPositiveCompletion(reply = ftp.getReplyCode())) {
        ftp.disconnect();
        throw new LauncherException("Failed to connect to ftp server");
      }
      
      ftp.user("ftp");
      if (!FTPReply.isPositiveCompletion(reply = ftp.getReplyCode())) {
        ftp.disconnect();
        throw new LauncherException("Failed to set ftp user");
      }
      if (!ftp.setFileType(FTPClient.BINARY_FILE_TYPE)) {
        ftp.disconnect();
        throw new LauncherException("Failed to set ftp file type");
      }

      if (runStatus != null) {
        ftp.setCopyStreamListener(new CopyStreamAdapter() {
          public void bytesTransferred(long totalBytesTransferred, int bytesTransferred, long streamSize) {
            float progress = ((float) totalBytesTransferred) / ((float) version.getExpectedFileSize());
            if (progress >= 0 && progress <= 100) {
              runStatus.update(RunStatus.Stage.downloading, progress);
            }
          }
        });
      }

      OutputStream outputStream = null;
      try {
        outputStream = new BufferedOutputStream(new FileOutputStream(local));
        boolean success = ftp.retrieveFile(version.downloadPath, outputStream);
        if (!success) {
          throw new LauncherException("Failed to download " + version.downloadPath);
        }
      } finally {
        if (outputStream != null) {
          try {
            outputStream.close();
          } catch (IOException ignored) {
          }
        }
      }
      
      ftp.logout();
    } catch (IOException e) {
      error = true;
      e.printStackTrace();
    } finally {
      if (ftp.isConnected()) {
        try {
          ftp.disconnect();
        } catch (IOException ignored) {
          
        }
      }
    }
    if (error) throw new LauncherException("Error whilst downloading file " + version.downloadPath);
  }
  
  private static byte[] hexStringToByteArray(String s) {
    if (s.length() % 2 != 0) throw new LauncherException("Invalid length hex string");
    int len = s.length();
    byte[] data = new byte[len / 2];
    for (int i = 0; i < len; i += 2) {
      data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4) + Character.digit(s.charAt(i + 1), 16));
    }
    return data;
  }
  
  private static byte[] sha1HashFile(File file)  {
    MessageDigest digest = null;
    InputStream fis = null;
    try {
      digest = MessageDigest.getInstance("SHA-1");
      fis = new FileInputStream(file);
      int n = 0;
      byte[] buffer = new byte[8192];
      while (n != -1) {
        n = fis.read(buffer);
        if (n > 0) {
          digest.update(buffer, 0, n);
        }
      }
    } catch (Exception e) {
      throw new LauncherException("Failed to sha1 hash file", e);
    } finally {
      if (fis != null) {
        try {
          fis.close();
        } catch (IOException ignored) {
          
        }
      }
    }
    return digest.digest();
  }
  
  private static File getLauncherFolder() {
    File homeDir = new File(System.getProperty("user.home"));
    String str = (System.getProperty("os.name")).toUpperCase();
    File cubesDir = null;
    if (str.contains("WIN")) {
      cubesDir =  new File(System.getenv("APPDATA"), "Cubes");
    } else if (str.contains("MAC")) {
      cubesDir = new File(new File(new File(homeDir, "Library"), "Application Support"), "Cubes");
    } else {
      cubesDir = new File(homeDir, ".Cubes");
    }
    File launcherDir = new File(cubesDir, "launcher");
    launcherDir.mkdirs();
    return launcherDir;
  }
}
