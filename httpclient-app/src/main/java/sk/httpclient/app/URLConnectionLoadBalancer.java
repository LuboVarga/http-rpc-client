package sk.httpclient.app;

import com.google.common.collect.Lists;
import com.netflix.client.DefaultLoadBalancerRetryHandler;
import com.netflix.client.RetryHandler;
import com.netflix.loadbalancer.*;
import com.netflix.loadbalancer.reactive.LoadBalancerCommand;
import com.netflix.loadbalancer.reactive.ServerOperation;
import rx.Observable;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;

/**
 * @author Allen Wang
 */
public class URLConnectionLoadBalancer {

    private final ILoadBalancer loadBalancer;
    // retry handler that does not retry on same server, but on a different server
    private final RetryHandler retryHandler = new DefaultLoadBalancerRetryHandler(0, 1, true);

    public URLConnectionLoadBalancer(List<Server> serverList) {
        loadBalancer = LoadBalancerBuilder.newBuilder().buildFixedServerListLoadBalancer(serverList);
    }

    public String call(final String path) throws Exception {
        return LoadBalancerCommand.<String>builder()
                .withLoadBalancer(loadBalancer)
                .build()
                .submit(new ServerOperation<String>() {
                    @Override
                    public Observable<String> call(Server server) {
                        URL url;
                        try {
                            url = new URL("http://" + server.getHost() + ":" + server.getPort() + path);
                            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                            return Observable.just(conn.getResponseMessage());
                        } catch (Exception e) {
                            return Observable.error(e);
                        }
                    }
                }).toBlocking().first();
    }

    public LoadBalancerStats getLoadBalancerStats() {
        return ((BaseLoadBalancer) loadBalancer).getLoadBalancerStats();
    }

    public static void main(String[] args) throws Exception {
        URLConnectionLoadBalancer urlLoadBalancer = new URLConnectionLoadBalancer(Lists.newArrayList(
                new Server("localhost", 8887),
                new Server("localhost", 8888),
                new Server("localhost", 8889)));
        for (int i = 0; i < 6; i++) {
            System.out.println(urlLoadBalancer.call("/test"));
        }
        System.out.println("=== Load balancer stats ===");
        System.out.println(urlLoadBalancer.getLoadBalancerStats());
    }
}