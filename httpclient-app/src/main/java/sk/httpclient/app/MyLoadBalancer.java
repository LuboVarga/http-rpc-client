package sk.httpclient.app;

import com.netflix.loadbalancer.Server;
import com.netflix.loadbalancer.ZoneAwareLoadBalancer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class MyLoadBalancer extends ZoneAwareLoadBalancer {
    private static final Logger LOG = LoggerFactory.getLogger(MyLoadBalancer.class);

    @Override
    public void addServers(List<Server> newServers) {
        super.addServers(newServers);
    }

    @Override
    public Server chooseServer(Object key) {
        try {
            Server server = super.chooseServer(key);
            LOG.trace("choosing: {}", (server == null ? "server is null " : server.getPort()));
            return server;
        } catch (NullPointerException ex) {
            System.out.println("NPE!!!");
            return null;
        }
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
