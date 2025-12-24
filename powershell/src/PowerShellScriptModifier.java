
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 *
 * @author steve
 */
/**
 * Utility class for modifying PowerShell scripts by appending output file names to Invoke-WebRequest commands.
 * 
 * This class provides functionality to:
 * <ul>
 *   <li>Read PowerShell scripts from an input directory</li>
 *   <li>Modify all "Invoke-WebRequest" commands with generated output file names</li>
 *   <li>Write the modified scripts to an output directory</li>
 * </ul>
 * 
 * <p>The modification transforms each occurrence of:
 * {@code Invoke-WebRequest} into {@code Invoke-WebRequest -OutFile <generated-name>}
 * where the generated name is a unique identifier combining nanosecond timestamp and UUID.
 * 
 * @see #main(String[]) for the entry point
 * @see #generateName() for unique file name generation
 */
public class powershellScriptModifier {

    /**
     * @param args the command line arguments
     */
    /**
     * Main entry point for the PowerShell script modification utility.
     * 
     * Reads a PowerShell script from the input directory, modifies all 
     * "Invoke-WebRequest" commands by appending a generated output file name,
     * and writes the modified script to the output directory.
     * 
     * The modification transforms:
     * {@code Invoke-WebRequest} → {@code Invoke-WebRequest -OutFile <generated-name>}
     * 
     * Input file: input/sample.ps1
     * Output file: output/sample.ps1
     * 
     * @param args command line arguments (not used)
     * 
     * @throws IOException if an error occurs reading or writing files,
     *         caught and printed to standard error stream
     */
    public static void main(String[] args) {
        Path inputPath = Path.of("input/sample.ps1");
        Path outPath= Path.of("output/sample.ps1");
        try {
            Files.write(outPath, Files.lines(inputPath)
                .map(word -> word.replaceAll("Invoke-WebRequest", "Invoke-WebRequest -OutFile " + generateName())) 
                .toList());
        } catch (IOException e) {
            System.err.println("Error writing file: " + e.getMessage());
        }
    }

    /**
     * Generates a unique filename for a video file.
     * 
     * The filename is constructed using a combination of the current system time in nanoseconds
     * and a randomly generated UUID to ensure uniqueness, with an ".mp4" extension.
     * 
     * @return a unique video filename in the format: {@code nanoTime-UUID.mp4}
     */
    public static String generateName() {
        return System.nanoTime() + "-" + UUID.randomUUID() + ".mp4";
    }

}
