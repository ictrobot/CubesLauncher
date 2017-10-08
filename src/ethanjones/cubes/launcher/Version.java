package ethanjones.cubes.launcher;

public class Version implements java.io.Serializable {
  final String name;
  final boolean release;
  final String downloadPath;
  
  private String stringHash;
  private byte[] hash;
  private long fileSize;
  
  Version(String name, boolean release, String downloadPath, long fileSize) {
    this.name = name;
    this.release = release;
    this.downloadPath = downloadPath;
    this.fileSize = fileSize;
  }
  
  @Override
  public String toString() {
    return name + (release ? " release " : " ") + downloadPath + " " + fileSize + " " + (stringHash != null ? " " + stringHash : "");
  }
  
  void setSHA1Hash(String stringHash, byte[] hash) {
    this.stringHash = stringHash;
    this.hash = hash;
  }
  
  byte[] getExpectedHash() {
    return hash;
  }
  
  String getExpectedHashString() {
    return stringHash;
  }

  public long getExpectedFileSize() {
    return fileSize;
  }
}
