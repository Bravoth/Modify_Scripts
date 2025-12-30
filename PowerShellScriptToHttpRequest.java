import java.io.IOException;
import java.io.InputStream;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A Java class that parses a PowerShell script containing Invoke-WebRequest
 * and converts it into an HttpRequest using Java's HttpClient.
 *
 * This class extracts:
 * - URI from Invoke-WebRequest -Uri
 * - Cookies from session.Cookies.Add lines
 * - User-Agent from session.UserAgent
 * - Headers from -Headers @{...} block
 */
public class PowerShellScriptToHttpRequest {

    private String uri;
    private String userAgent;
    private List<HttpCookie> cookies = new ArrayList<>();
    private List<Header> headers = new ArrayList<>();

    record Header(String name, String value) {}

    /**
     * Parses the PowerShell script and builds the HttpRequest components.
     * @param scriptPath Path to the PowerShell script file
     * @throws IOException if file cannot be read
     */
    public void parseScript(String content) throws IOException {
        // Extract URI
        Pattern uriPattern = Pattern.compile("-Uri\s+.([^\"]+).");
        Matcher uriMatcher = uriPattern.matcher(content);
        if (uriMatcher.find()) {
            this.uri = uriMatcher.group(1);
        }

        // Extract User-Agent
        Pattern uaPattern = Pattern.compile(".session.UserAgent\s*=\s*.([^\"]+).");
        Matcher uaMatcher = uaPattern.matcher(content);
        if (uaMatcher.find()) {
            this.userAgent = uaMatcher.group(1);
        }

        // Extract cookies
        Pattern cookiePattern = Pattern.compile(
            "\\$session.Cookies.Add\\(\\(New-Object System\\.Net\\.Cookie\\(\"([^\"]+)\",\\s*\"([^\"]*)\",\\s*\"([^\"]*)\",\\s*\"([^\"]*)\"\\)\\)\\)"
        );
        Matcher cookieMatcher = cookiePattern.matcher(content);
        while (cookieMatcher.find()) {
            String name = cookieMatcher.group(1);
            String value = cookieMatcher.group(2);
            String path = cookieMatcher.group(3);
            String domain = cookieMatcher.group(4);
            HttpCookie cookie = new HttpCookie(name, value);
            cookie.setPath(path);
            cookie.setDomain(domain);
            cookies.add(cookie);
        }

        // Extract headers
        Pattern headerBlockPattern = Pattern.compile("-Headers\s*@\\{([\s\\S]*?)\\}");
        Matcher headerBlockMatcher = headerBlockPattern.matcher(content);
        if (headerBlockMatcher.find()) {
            String headerBlock = headerBlockMatcher.group(1);
            Pattern headerPattern = Pattern.compile(".([^\"]+).\s*=\s*.([^\"]+).");
            Matcher headerMatcher = headerPattern.matcher(headerBlock);
            while (headerMatcher.find()) {
                String name = headerMatcher.group(1);
                String value = headerMatcher.group(2);
                // Handle escaped quotes in PowerShell
                value = value.replaceAll("`\"", "\"");
                headers.add(new Header(name, value));
            }
        }
    }

    /**
     * Builds and returns an HttpRequest based on the parsed script.
     * @return HttpRequest configured with URI, headers, and cookies
     */
    public HttpRequest buildHttpRequest() {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(uri))
                .timeout(Duration.ofMinutes(2))
                .GET();

        // Add User-Agent if available
        if (userAgent != null) {
            builder.header("User-Agent", userAgent);
        }

        // Add parsed headers
        for (Header header : headers) {
            builder.header(header.name, header.value);
        }

        return builder.build();
    }

    /**
     * Creates an HttpClient with CookieManager containing the parsed cookies.
     * @return HttpClient configured with cookies
     */
    public HttpClient buildHttpClient() {
        CookieManager cookieManager = new CookieManager(null, CookiePolicy.ACCEPT_ALL);

        // Add cookies to cookie store
        for (HttpCookie cookie : cookies) {
            // Use the domain from cookie, but construct proper URI
            String domain = cookie.getDomain();
            if (domain.startsWith(".")) {
                domain = domain.substring(1); // Remove leading dot
            }
            try {
                URI cookieUri = URI.create("https://" + domain + "/");
                cookieManager.getCookieStore().add(cookieUri, cookie);
            } catch (Exception e) {
                System.err.println("Error adding cookie: " + e.getMessage());
            }
        }

        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .cookieHandler(cookieManager)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    /**
     * Executes the request and saves the response body to a file.
     * @param client HttpClient to use
     * @param request HttpRequest to execute
     * @param outputPath Path to save the response
     * @throws IOException if file operations fail
     * @throws InterruptedException if request is interrupted
     */
    public void executeAndSave(HttpClient client, HttpRequest request, Path outputPath)
            throws IOException, InterruptedException {
        try (InputStream is = client.send(request, HttpResponse.BodyHandlers.ofInputStream()).body()) {
            Files.copy(is, outputPath);
            System.out.println("Saved to " + outputPath.toAbsolutePath());
        }
    }

    // Getters for debugging
    public String getUri() { return uri; }
    public String getUserAgent() { return userAgent; }
    public List<HttpCookie> getCookies() { return cookies; }
    public List<Header> getHeaders() { return headers; }

    /**
     * Processes a single request from the parsed script part.
     * @param word the script part to process
     * @throws Exception if processing fails
     */
    private void processRequest(String word) {
        try {
            String outputFile = System.nanoTime() + "-" + UUID.randomUUID() + ".mp4";
            this.parseScript(word);

            HttpClient client = this.buildHttpClient();
            HttpRequest request = this.buildHttpRequest();

            Path outputPath = Path.of(outputFile);
            this.executeAndSave(client, request, outputPath);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Main method to demonstrate parsing a PowerShell script and making the request.
     * Usage: java PowerShellScriptToHttpRequest <script_path> [output_file]
     */
    public void main(String[] args) throws Exception {
        Path scriptPath = Path.of("input/sample.ps1");

        String content = Files.readString(scriptPath, StandardCharsets.UTF_8);
        List<String> filteredContent = List.of(content.split("};"));

        filteredContent.forEach(this::processRequest);
    }
}
