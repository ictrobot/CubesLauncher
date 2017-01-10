package ethanjones.cubes.launcher;

public class LauncherException extends RuntimeException {
  
  LauncherException(String message) {
    super(message);
  }
  
  LauncherException(String message, Throwable throwable) {
    super(message, throwable);
  }
}
