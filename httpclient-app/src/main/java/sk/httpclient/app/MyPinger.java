package sk.httpclient.app;

import com.netflix.loadbalancer.PingUrl;
import com.netflix.loadbalancer.Server;

public class MyPinger extends PingUrl {

    @Override
    public boolean isAlive(Server server) {
        System.out.println("Ping server " + server.getPort());
        return super.isAlive(server);
    }
}
