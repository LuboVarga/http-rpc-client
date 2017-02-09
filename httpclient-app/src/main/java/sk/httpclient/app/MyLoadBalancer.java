package sk.httpclient.app;

import com.netflix.loadbalancer.ILoadBalancer;
import com.netflix.loadbalancer.Server;

import java.util.List;

public class MyLoadBalancer implements ILoadBalancer {
    private int c = 0;

    @Override

    public void addServers(List<Server> newServers) {
        //super.addServers(newServers);
    }

    private final Server[] a = new Server[]{
        new Server("localhost", 8887),
        new Server("localhost", 8888),
        new Server("localhost", 8889)
    };

    @Override
    public Server chooseServer(Object key) {
        c = ++c % 3;
        return a[c];
    }

    @Override
    public void markServerDown(Server server) {
    }

    @Override
    public List<Server> getServerList(boolean availableOnly) {
        return null;
    }

    @Override
    public List<Server> getReachableServers() {
        return null;
    }

    @Override
    public List<Server> getAllServers() {
        return null;
    }
}
