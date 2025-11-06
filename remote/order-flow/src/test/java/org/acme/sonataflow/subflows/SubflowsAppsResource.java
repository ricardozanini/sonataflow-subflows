package org.acme.sonataflow.subflows;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

import static org.awaitility.Awaitility.await;

/**
 * Lifecycle Manager to run the subflows during test.
 * It's not a recommended approach for a production environment, rather prefer mocking the services.
 * We are doing it this way just to exemplify in tests how the workflows interact with each other remotely.
 */
public class SubflowsAppsResource implements QuarkusTestResourceLifecycleManager {

    private Process fraudProc;
    private Process shippingProc;

    private static void waitReady(String url) {
        await()
                .atMost(Duration.ofSeconds(60))
                .pollInterval(Duration.ofMillis(200))
                .ignoreExceptions()
                .until(() -> is2xx(url));
    }

    private static boolean is2xx(String url) throws Exception {
        HttpURLConnection con = (HttpURLConnection) URI.create(url).toURL().openConnection();
        con.setConnectTimeout(1500);
        con.setReadTimeout(1500);
        con.setRequestMethod("GET");
        int code = con.getResponseCode();
        return code >= 200 && code < 300;
    }

    private static void kill(Process p) {
        if (p != null && p.isAlive()) {
            p.destroy();
            try {
                p.waitFor();
            } catch (InterruptedException ignored) {
            }
            if (p.isAlive()) p.destroyForcibly();
        }
    }

    private static String javaCmd() {
        String home = System.getProperty("java.home");
        return Path.of(home, "bin", isWindows() ? "java.exe" : "java").toString();
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    @Override
    public Map<String, String> start() {
        try {
            Path itDir = Path.of("target", "it-binaries").toAbsolutePath();
            Path fraudJar = itDir.resolve("sonataflow-subflows-remote-fraud-1.0-SNAPSHOT-runner.jar");
            Path shippingJar = itDir.resolve("sonataflow-subflows-remote-shipping-1.0-SNAPSHOT-runner.jar");

            // Same as defined in the application.properties
            String fraudPort = "8082";
            String shippingPort = "8083";

            fraudProc = new ProcessBuilder(javaCmd(),
                    "-Dquarkus.http.port=" + fraudPort,
                    "-Dquarkus.profile=test",
                    "-jar", fraudJar.toString())
                    .inheritIO()
                    .start();

            shippingProc = new ProcessBuilder(javaCmd(),
                    "-Dquarkus.http.port=" + shippingPort,
                    "-Dquarkus.profile=test",
                    "-jar", shippingJar.toString())
                    .inheritIO()
                    .start();

            waitReady("http://localhost:" + fraudPort + "/q/health/ready");
            waitReady("http://localhost:" + shippingPort + "/q/health/ready");

            return Collections.emptyMap();
        } catch (IOException e) {
            throw new RuntimeException("Failed to start subflow apps", e);
        }
    }

    @Override
    public void stop() {
        kill(fraudProc);
        kill(shippingProc);
    }
}

