package sk.httpclient.app;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.client.config.CommonClientConfigKey;
import com.netflix.client.config.IClientConfig;
import com.netflix.ribbon.ClientOptions;
import com.netflix.ribbon.Ribbon;
import com.netflix.ribbon.RibbonRequest;
import com.netflix.ribbon.http.HttpRequestTemplate;
import com.netflix.ribbon.http.HttpResourceGroup;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.reactivex.netty.protocol.http.client.HttpClientResponse;
import rx.Observable;
import rx.functions.Func1;

import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class RibbonHttpClient<R, T> implements MyHttpClient<R, T> {

    private final ObjectMapper mapperDefault = new ObjectMapper();
    //    HttpRequestTemplate<ByteBuf> service;
//    RibbonRequest<ByteBuf> req;
    private HttpRequestTemplate.Builder<ByteBuf> builder;

    public RibbonHttpClient(String servers) {
        HttpResourceGroup resourceGroup = Ribbon.createHttpResourceGroup("sample-client", config(servers));
        builder = resourceGroup.newTemplateBuilder("sample-client");
//        service = builder
//                .withMethod("GET")
//                .withUriTemplate("/test/record")
//                //.withUriTemplate("/problems/systemExit")
//                //.withUriTemplate("/problems/stackOverflow")
//                //.withUriTemplate("/problems/runtimeException")
//                //.withHystrixProperties(com.netflix.hystrix.HystrixObservableCommand.Setter.withGroupKey())
//                .build();
//        req = service.requestBuilder().build();

    }

    private Func1<HttpClientResponse<ByteBuf>, Observable<T>> getContent(Class<T> clazz) {
        return x -> x.getContent().map(data -> convert(clazz, data));
    }

    private T convert(Class<T> clazz, ByteBuf buf) {
        try {
            byte[] bytes = new byte[buf.readableBytes()];
            buf.readBytes(bytes);
            T t;
            try {
                t = mapperDefault.readValue(bytes, clazz);
            } catch (Exception ex) {
                throw new RuntimeException("clazz=" + clazz + ";buf=" + new String(bytes) + "...;", ex);
            }
            return t;
        } catch (RuntimeException e) {
            e.printStackTrace();
        }
        return null;
    }

    private ClientOptions config(String servers) {
        IClientConfig clientConfig = IClientConfig.Builder.newBuilder("sample-client").build();
        //clientConfig.set(CommonClientConfigKey.NFLoadBalancerClassName, "sk.httpclient.app.MyLoadBalancer");
        clientConfig.set(CommonClientConfigKey.InitializeNFLoadBalancer, true);
        clientConfig.set(CommonClientConfigKey.ListOfServers, servers);
        clientConfig.set(CommonClientConfigKey.MaxAutoRetriesNextServer, 1);
        clientConfig.set(CommonClientConfigKey.MaxAutoRetries, 1);
        clientConfig.set(CommonClientConfigKey.EnableConnectionPool, true);
        clientConfig.set(CommonClientConfigKey.PoolMaxThreads, 50);
        clientConfig.set(CommonClientConfigKey.PoolMinThreads, 42);
        clientConfig.set(CommonClientConfigKey.PoolKeepAliveTime, 100000000);
        clientConfig.set(CommonClientConfigKey.PoolKeepAliveTimeUnits, TimeUnit.SECONDS.toString()); //TODO ake su tu hodnoty?
        clientConfig.set(CommonClientConfigKey.MaxConnectionsPerHost, 20);
        clientConfig.set(CommonClientConfigKey.MaxTotalConnections, 70);
        clientConfig.set(CommonClientConfigKey.EnablePrimeConnections, true);
        clientConfig.set(CommonClientConfigKey.EnablePrimeConnections, true);
        return ClientOptions.from(clientConfig);
    }


    @Override
    public Future<T> send(String procedureName, R request, Class<T> clazz) throws JsonProcessingException {
        HttpRequestTemplate<ByteBuf> service = builder
                .withMethod("POST")
                .withUriTemplate(procedureName)
                .build();

        RibbonRequest<ByteBuf> req = service.requestBuilder().withContent(toJson(request)).build();

        return req.toObservable()
                .map(buff -> convert(clazz, buff))
                .doOnError(throwable -> {
                    throw new RuntimeException("Error while doing RPC!", throwable);
                })
                .toBlocking()
                .toFuture();
    }

    private Observable<ByteBuf> toJson(R request) throws JsonProcessingException {
        ByteBuf buf = Unpooled.copiedBuffer(mapperDefault.writeValueAsString(request).getBytes());
        return Observable.just(buf);
    }

    // minimal testing code with Tomas
    public static void main(String[] args) {
        HttpResourceGroup httpResourceGroup = Ribbon.createHttpResourceGroup("movieServiceClient",
                ClientOptions.create()
                        .withMaxAutoRetriesNextServer(0)
                        .withConfigurationBasedServerList("localhost:8889,localhost:8888,localhost:8887"));
        HttpRequestTemplate<ByteBuf> recommendationsByUserIdTemplate = httpResourceGroup.newTemplateBuilder("recommendationsByUserId", ByteBuf.class)
                .withMethod("POST")
                .withUriTemplate("/test/record")
                .build();
        for (int i = 0; i < 500; i++) {
            try {
                final String x = recommendationsByUserIdTemplate.requestBuilder()
                        .build()
                        .execute().toString();
                System.out.println(x);
            } catch (Exception e) {
                System.out.println("nepreslo: " + e.getMessage());
            }
        }
    }
}
