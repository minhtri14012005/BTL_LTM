package vn.edu.multigame.common.config;

import java.net.Inet4Address;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/** Supplies LAN defaults for every launcher, including Spring Dashboard. */
public class LanEnvironmentPostProcessor implements EnvironmentPostProcessor {
    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String port = environment.getProperty("server.port", "8080");
        var origins = new LinkedHashSet<String>();
        origins.add("http://localhost:" + port);
        origins.add("http://127.0.0.1:" + port);
        origins.add("http://localhost:5173");
        origins.add("http://127.0.0.1:5173");
        addLanOrigins(origins, port);
        // Lowest priority: explicit environment variables and config files still win.
        environment.getPropertySources().addLast(new MapPropertySource("classQuestLanDefaults",
                Map.of("ALLOWED_ORIGINS", String.join(",", origins), "SERVER_ADDRESS", "0.0.0.0")));
    }

    protected void addLanOrigins(Set<String> origins, String port) {
        try {
            var interfaces = NetworkInterface.getNetworkInterfaces();
            if (interfaces == null) return;
            for (var network : Collections.list(interfaces)) {
                if (!network.isUp()) continue;
                for (var address : Collections.list(network.getInetAddresses())) {
                    if (address instanceof Inet4Address && !address.isLoopbackAddress()) {
                        origins.add("http://" + address.getHostAddress() + ":" + port);
                    }
                }
            }
        } catch (SocketException failure) {
            throw new IllegalStateException("Cannot detect LAN addresses; check the network interfaces.", failure);
        }
    }
}
