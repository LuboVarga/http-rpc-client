package sk.httpclient.client;

import com.netflix.loadbalancer.Server;
import com.netflix.loadbalancer.ZoneAwareLoadBalancer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class LoggingLoadBalancer extends ZoneAwareLoadBalancer {
    private static final Logger LOG = LoggerFactory.getLogger(LoggingLoadBalancer.class);

    @Override
    public void addServers(List<Server> newServers) {
        super.addServers(newServers);
    }

    @Override
    public Server chooseServer(Object key) {
        Server server = null;
        try {
            server = super.chooseServer(key);
            LOG.trace("choosing: {}", server);
            return server;
        } catch (NullPointerException ex) {
            LOG.error("choosing: " + server + " returning null as chosen server", ex);
        }

        return null;
    }

    @Override
    public void markServerDown(Server server) {
        super.markServerDown(server);
    }

    @Override
    public List<Server> getServerList(boolean availableOnly) {
        return super.getServerList(availableOnly);
    }

    @Override
    public List<Server> getReachableServers() {
        return super.getReachableServers();
    }

    @Override
    public List<Server> getAllServers() {
        return super.getAllServers();
    }
}
