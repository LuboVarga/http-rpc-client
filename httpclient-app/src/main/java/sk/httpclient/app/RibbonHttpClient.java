package sk.httpclient.app;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import com.netflix.client.config.CommonClientConfigKey;
import com.netflix.client.config.IClientConfig;
import com.netflix.hystrix.HystrixCommandGroupKey;
import com.netflix.hystrix.HystrixCommandKey;
import com.netflix.hystrix.HystrixObservableCommand;
import com.netflix.ribbon.ClientOptions;
import com.netflix.ribbon.Ribbon;
import com.netflix.ribbon.RibbonRequest;
import com.netflix.ribbon.http.HttpRequestTemplate;
import com.netflix.ribbon.http.HttpResourceGroup;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.reactivex.netty.protocol.http.client.HttpClientResponse;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import rx.Observable;
import rx.functions.Func1;

import java.net.ConnectException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class RibbonHttpClient<R, T> implements MyHttpClient<R, T> {

    public static final String NAME = "sample-client";
    private final ObjectMapper mapperDefault = new ObjectMapper();
    //    HttpRequestTemplate<ByteBuf> service;
//    RibbonRequest<ByteBuf> req;
    private HttpRequestTemplate.Builder<ByteBuf> builder;

    public RibbonHttpClient(String servers) {
        mapperDefault.registerModule(new ParameterNamesModule());
        mapperDefault.registerModule(new Jdk8Module());
        //mapperDefault.registerModule(new JavaTimeModule());

        HttpResourceGroup resourceGroup = Ribbon.createHttpResourceGroup(NAME, config(servers));
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
        if(clazz.equals(String.class)) {
            return (T) new String(buf.array());
        }
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
        clientConfig.set(CommonClientConfigKey.MaxAutoRetriesNextServer, 0);
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
    @SuppressWarnings("unchecked")
    public Future<T> send(String procedureName, R request, Class<T> clazz) throws JsonProcessingException {

        RetryPolicy retryPolicy = new RetryPolicy()
                .retryOn(ConnectException.class)
                .withDelay(1, TimeUnit.SECONDS);

        return Failsafe.with(retryPolicy).get(() -> sendInternal(procedureName, request, clazz));
    }

    private Future<T> sendInternal(String procedureName, R request, Class<T> clazz) throws JsonProcessingException {
        HystrixCommandGroupKey key = HystrixCommandGroupKey.Factory.asKey(NAME);

        HttpRequestTemplate<ByteBuf> service = builder
                .withMethod("POST")
                .withHystrixProperties(HystrixObservableCommand.Setter.withGroupKey(key).andCommandKey(HystrixCommandKey.Factory.asKey(procedureName))) //TODO maybe cache?
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

}
