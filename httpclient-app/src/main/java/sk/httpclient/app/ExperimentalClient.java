package sk.httpclient.app;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.client.DefaultLoadBalancerRetryHandler;
import com.netflix.client.RetryHandler;
import com.netflix.loadbalancer.ILoadBalancer;
import com.netflix.loadbalancer.LoadBalancerBuilder;
import com.netflix.loadbalancer.Server;
import com.netflix.loadbalancer.reactive.LoadBalancerCommand;
import rx.Observable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.concurrent.Future;

/**
 * @author Allen Wang
 */
public class ExperimentalClient<R, T> implements MyHttpClient<R, T> {

    private ObjectMapper mapper = new ObjectMapper();

    private final ILoadBalancer loadBalancer;
    private final RetryHandler retryHandler = new DefaultLoadBalancerRetryHandler(2, 1, true);
    LoadBalancerCommand<T> build;

    public ExperimentalClient(List<Server> serverList) {
        loadBalancer = LoadBalancerBuilder.newBuilder().buildFixedServerListLoadBalancer(serverList);
        build = LoadBalancerCommand.<T>builder()
                .withRetryHandler(retryHandler)
                .withLoadBalancer(loadBalancer)
                .build();
    }


    private T convert(Class<T> clazz, String buf) {
        try {
            return mapper.readValue(buf, clazz);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }


    private Observable<T> makeCall(Server server, String path, Class<T> clazz) {
        System.out.print("making call: " + server.getPort());
        URL url;
        try {
            url = new URL("http://" + server.getHost() + ":" + server.getPort() + path);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            return Observable.just(convert(clazz, convertStreamToString(conn)));
        } catch (Exception e) {
            return Observable.error(e);
        }
    }


    @Override
    public Future<T> send(String procedureName, R request, Class <T> clazz) {
        return build.submit(server -> makeCall(server, procedureName, clazz)).toBlocking().toFuture();
    }

    @Override
    public Future<T> sendIdempotent(String procedureName, R request, Class<T> clazz) throws JsonProcessingException {
        // TODO implement correctly?
        return build.submit(server -> makeCall(server, procedureName, clazz)).toBlocking().toFuture();
    }


    private static String convertStreamToString(HttpURLConnection conn) throws IOException {

        InputStream is = conn.getInputStream();

        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        StringBuilder sb = new StringBuilder();

        String line = null;
        try {
            while ((line = reader.readLine()) != null) {
                sb.append(line + "\n");
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                is.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return sb.toString();

    }

}