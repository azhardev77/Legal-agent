import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Probe2 {
  public static void main(String[] args) {
    Path home = Paths.get(System.getProperty("java.home"));
    System.out.println("javahome=" + home);
    try {
      System.out.println("javahome.real=" + home.toRealPath());
    } catch (Exception e) {
      System.out.println("JAVAHOME_FAIL=" + e);
    }
    try {
      System.out.println("cwd.real=" + Paths.get(".").toRealPath());
    } catch (Exception e) {
      System.out.println("CWD_FAIL=" + e);
    }
    try {
      Path tmp = Paths.get("docs/test-logs/hello/deleteme.tmp");
      Files.writeString(tmp, "x");
      Files.delete(tmp);
      System.out.println("DELETE_OK");
    } catch (Exception e) {
      System.out.println("DELETE_FAIL=" + e);
    }
    System.out.println("DONE");
  }
}
