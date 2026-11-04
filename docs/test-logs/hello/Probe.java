import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Probe {
  public static void main(String[] args) {
    Path p = Paths.get("docs/test-logs/hello/out/hello.jar");
    System.out.println("exists=" + Files.exists(p));
    try {
      System.out.println("real=" + p.toRealPath());
    } catch (Exception e) {
      System.out.println("TOREALPATH_FAIL=" + e);
    }
    try (FileSystem fs = FileSystems.newFileSystem(p)) {
      System.out.println("ZIPFS_OPEN_OK");
    } catch (Exception e) {
      System.out.println("ZIPFS_FAIL=" + e);
    }
    System.out.println("DONE");
  }
}
