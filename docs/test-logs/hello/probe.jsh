import java.nio.file.*;
var p = Paths.get("docs/test-logs/hello/out/hello.jar");
System.out.println("exists=" + Files.exists(p));
try { System.out.println("real=" + p.toRealPath()); } catch (Exception e) { System.out.println("TOREALPATH_FAIL=" + e); }
try { var fs = FileSystems.newFileSystem(p); System.out.println("ZIPFS_OPEN_OK"); fs.close(); System.out.println("ZIPFS_CLOSE_OK"); } catch (Exception e) { System.out.println("ZIPFS_FAIL=" + e); }
/exit
