package ethanjones.cubes.launcher;

public class Version implements java.io.Serializable {
  final String name;
  final boolean release;
  final String downloadPath;
  
  private String stringHash;
  private byte[] hash;
  
  Version(String name, boolean release, String downloadPath) {
    this.name = name;
    this.release = release;
    this.downloadPath = downloadPath;
  }
  
  @Override
  public String toString() {
    return name + (release ? " release " : " ") + downloadPath + (stringHash != null ? " " + stringHash : "");
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
}
