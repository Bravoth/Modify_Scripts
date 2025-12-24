import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import static org.junit.jupiter.api.Assertions.*;

public class powershellScriptModifierTest {

    private Path inputDir;
    private Path outputDir;

    @BeforeEach
    public void setUp() throws IOException {
        inputDir = Paths.get("input");
        outputDir = Paths.get("output");
        Files.createDirectories(inputDir);
        Files.createDirectories(outputDir);
    }

    @AfterEach
    public void tearDown() throws IOException {
        Files.walk(outputDir).sorted((a, b) -> b.compareTo(a)).forEach(path -> {
            try {
                Files.delete(path);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    @Test
    public void testGenerateName() {
        String name1 = powershellScriptModifier.generateName();
        String name2 = powershellScriptModifier.generateName();
        
        assertTrue(name1.endsWith(".mp4"));
        assertTrue(name2.endsWith(".mp4"));
        assertNotEquals(name1, name2);
    }

    @Test
    public void testMainModifiesInvokeWebRequest() throws IOException {
        Files.write(Paths.get("input/sample.ps1"), 
            "Invoke-WebRequest -Uri https://example.com\nInvoke-WebRequest -Uri https://test.com\n".getBytes());
        
        powershellScriptModifier.main(new String[]{});
        
        String output = Files.readString(Paths.get("output/sample.ps1"));
        assertTrue(output.contains("Invoke-WebRequest -OutFile"));
        assertFalse(output.contains("Invoke-WebRequest -Uri"));
    }

    @Test
    public void testMainWithMixedContent() throws IOException {
        Files.write(Paths.get("input/sample.ps1"), 
            "# Comment\nInvoke-WebRequest\nOther command\n".getBytes());
        
        powershellScriptModifier.main(new String[]{});
        
        String output = Files.readString(Paths.get("output/sample.ps1"));
        assertTrue(output.contains("Invoke-WebRequest -OutFile"));
        assertTrue(output.contains("# Comment"));
    }
}