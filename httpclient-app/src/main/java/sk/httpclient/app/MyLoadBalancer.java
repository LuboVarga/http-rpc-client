package sk.httpclient.app;

import com.netflix.loadbalancer.DynamicServerListLoadBalancer;
import com.netflix.loadbalancer.Server;

import java.util.List;

public class MyLoadBalancer extends DynamicServerListLoadBalancer {

    private static int c = 0;

    @Override

    public void addServers(List<Server> newServers) {
        System.out.println("My balancer was called");
        super.addServers(newServers);
    }

    @Override
    public Server chooseServer(Object key) {
        c = ++c % upServerList.size();
        System.out.println("My balancer was called");

        return upServerList.get(c);
    }

    @Override
    public void markServerDown(Server server) {
        System.out.println("My balancer was called");

    }

    @Override
    public List<Server> getServerList(boolean availableOnly) {
        System.out.println("My balancer was called");

        return null;
    }

    @Override
    public List<Server> getReachableServers() {
        System.out.println("My balancer was called");

        return null;
    }

    @Override
    public List<Server> getAllServers() {
        System.out.println("My balancer was called");

        return null;
    }
}
