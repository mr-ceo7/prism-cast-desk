import java.io.File;
import java.io.InputStream;

public class TestWayland {
    public static void main(String[] args) {
        String tempPath = "/tmp/prism_cast_test.png";
        try {
            File f = new File(tempPath);
            if (f.exists()) f.delete();

            System.out.println("Running gdbus screenshot command...");
            ProcessBuilder pb = new ProcessBuilder(
                "gdbus", "call", "--session",
                "--dest", "org.gnome.Shell.Screenshot",
                "--object-path", "/org/gnome/Shell/Screenshot",
                "--method", "org.gnome.Shell.Screenshot.Screenshot",
                "false", "false", tempPath
            );
            
            // Set DBUS_SESSION_BUS_ADDRESS if it exists in the environment
            // In a daemon/terminal running via ssh or IDE background execution, we might need to specify the address
            Process p = pb.start();
            
            // Wait up to 3 seconds
            int exitCode = p.waitFor();
            System.out.println("Exit code: " + exitCode);
            
            if (f.exists()) {
                System.out.println("SUCCESS! Screenshot created at " + tempPath + " (size: " + f.length() + " bytes)");
                f.delete();
            } else {
                System.out.println("FAILED! File was not created.");
                // Print error stream
                InputStream es = p.getErrorStream();
                byte[] err = es.readAllBytes();
                System.out.println("Error: " + new String(err));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
