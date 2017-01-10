package ethanjones.cubes.launcher;

import org.apache.commons.net.ftp.*;

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
    SwingUtilities.invokeLater(new Runnable() {
      @Override
      public void run() {
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
        versions.put(v, new Version(v, true, path));
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
        versions.put(v, new Version(v, false, path));
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
    if (error) throw new LauncherException("Error whilst downloading versions");
  }
  
  public static void run(Version version) {
    File baseFolder = getBaseFolder();
    File launcherFolder = new File(baseFolder, "launcher");
    File versionFolder = new File(launcherFolder, "versions");
    versionFolder.mkdirs();
    
    File jarFile = new File(versionFolder, version.name + ".jar");
    boolean update = true;
    if (jarFile.exists()) {
      byte[] fileHash = sha1HashFile(jarFile);
      if (Arrays.equals(fileHash, version.getExpectedHash())) {
        System.out.println(jarFile + " matches hash: " + version.getExpectedHashString());
        update = false;
      } else {
        System.out.println(jarFile + " does not matches hash: " + version.getExpectedHashString());
        jarFile.delete();
      }
    }
    if (update) {
      downloadFile(version.downloadPath, jarFile);
    }
    
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
  
  private static void downloadFile(String remote, File local) {
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
  
      OutputStream outputStream = null;
      try {
        outputStream = new BufferedOutputStream(new FileOutputStream(local));
        boolean success = ftp.retrieveFile(remote, outputStream);
        if (!success) {
          throw new LauncherException("Failed to download " + remote);
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
    if (error) throw new LauncherException("Error whilst downloading file " + remote);
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
  
  private static File getBaseFolder() {
    File homeDir = new File(System.getProperty("user.home"));
    String str = (System.getProperty("os.name")).toUpperCase();
    if (str.contains("WIN")) {
      return new File(System.getenv("APPDATA"), "Cubes");
    } else if (str.contains("MAC")) {
      return new File(new File(new File(homeDir, "Library"), "Application Support"), "Cubes");
    } else {
      return new File(homeDir, ".Cubes");
    }
  }
}
