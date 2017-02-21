package sk.httpclient.client;

import com.netflix.loadbalancer.PingUrl;
import com.netflix.loadbalancer.Server;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LoggingPinger extends PingUrl {

    private static final Logger LOG = LoggerFactory.getLogger(RibbonHttpClient.class);

    @Override
    public boolean isAlive(Server server) {
        boolean alive = super.isAlive(server);
        LOG.trace("Server {} is alive: {}", server, alive);
        return alive;
    }
}
