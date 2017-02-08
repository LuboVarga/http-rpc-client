package sk.httpclient.app;

import com.netflix.loadbalancer.DynamicServerListLoadBalancer;
import com.netflix.loadbalancer.Server;

import java.util.List;

public class MyLoadBalancer extends DynamicServerListLoadBalancer {

    private int c = 0;

    @Override

    public void addServers(List<Server> newServers) {
        super.addServers(newServers);
    }

    @Override
    public Server chooseServer(Object key) {
        //c = ++c % upServerList.size();
        //return upServerList.get(c);
        return super.chooseServer(key);
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
