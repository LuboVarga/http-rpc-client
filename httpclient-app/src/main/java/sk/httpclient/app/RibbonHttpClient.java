package sk.httpclient.app;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import com.netflix.client.config.CommonClientConfigKey;
import com.netflix.client.config.IClientConfig;
import com.netflix.hystrix.HystrixCommandGroupKey;
import com.netflix.hystrix.HystrixCommandKey;
import com.netflix.hystrix.HystrixCommandProperties;
import com.netflix.hystrix.HystrixObservableCommand;
import com.netflix.hystrix.exception.HystrixBadRequestException;
import com.netflix.ribbon.*;
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

    private static final String NAME = "sample-client";
    private final ObjectMapper mapperDefault = new ObjectMapper();
    private HttpRequestTemplate.Builder<ByteBuf> builder;
    private RetryPolicy retryPolicy = new RetryPolicy()
            .retryOn(ConnectException.class)
            .withMaxRetries(1)
            .withDelay(500, TimeUnit.MILLISECONDS);

    private RetryPolicy idempotentRetryPolicy = new RetryPolicy()
            .retryOn(ConnectException.class, UnsuccessfulResponseException.class, HystrixBadRequestException.class)
            .withMaxRetries(1)
            .withDelay(500, TimeUnit.MILLISECONDS);

    private HystrixCommandGroupKey key = HystrixCommandGroupKey.Factory.asKey(NAME);

    public RibbonHttpClient(String servers) {
        mapperDefault.registerModule(new ParameterNamesModule());
        mapperDefault.registerModule(new Jdk8Module());
        //mapperDefault.registerModule(new JavaTimeModule());

        HttpResourceGroup resourceGroup = Ribbon.createHttpResourceGroup(NAME, config(servers));
        builder = resourceGroup.newTemplateBuilder("sample-client");
    }

    private Func1<HttpClientResponse<ByteBuf>, Observable<T>> getContent(Class<T> clazz) {
        return x -> x.getContent().map(data -> convert(clazz, data));
    }

    private T convert(Class<T> clazz, ByteBuf buf) {
        if (clazz.equals(String.class)) {
            //copy() is solving on CompositeByteBuf instance exception "java.lang.UnsupportedOperationException: direct buffer"
            return (T) new String(buf.copy().array());
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

        clientConfig.set(CommonClientConfigKey.NFLoadBalancerClassName, "sk.httpclient.app.MyLoadBalancer");
        clientConfig.set(CommonClientConfigKey.InitializeNFLoadBalancer, true);
        clientConfig.set(CommonClientConfigKey.ListOfServers, servers);
        clientConfig.set(CommonClientConfigKey.MaxAutoRetriesNextServer, 0);
        clientConfig.set(CommonClientConfigKey.MaxAutoRetries, 1);
        clientConfig.set(CommonClientConfigKey.EnableConnectionPool, true);
        clientConfig.set(CommonClientConfigKey.PoolMaxThreads, 50);
        clientConfig.set(CommonClientConfigKey.PoolMinThreads, 42);
        clientConfig.set(CommonClientConfigKey.NFLoadBalancerPingClassName, "sk.httpclient.app.MyPinger");
        clientConfig.set(CommonClientConfigKey.PoolKeepAliveTime, 100000000);
        clientConfig.set(CommonClientConfigKey.PoolKeepAliveTimeUnits, TimeUnit.SECONDS.toString()); //TODO ake su tu hodnoty?
        clientConfig.set(CommonClientConfigKey.MaxConnectionsPerHost, 20);
        clientConfig.set(CommonClientConfigKey.MaxTotalConnections, 70);
        clientConfig.set(CommonClientConfigKey.EnablePrimeConnections, true);
        return ClientOptions.from(clientConfig);
    }


    @Override
    @SuppressWarnings("unchecked")
    public Future<T> send(String procedureName, R request, Class<T> clazz) throws JsonProcessingException {
        return Failsafe.with(retryPolicy).get(() -> sendInternal(procedureName, request, clazz));
    }

    @Override
    public Future<T> sendIdempotent(String procedureName, R request, Class<T> clazz) throws JsonProcessingException {
        return Failsafe.with(idempotentRetryPolicy).get(() -> sendInternal(procedureName, request, clazz));
    }

    @Override
    public T sendIdempotentImmidiate(String procedureName, R request, Class<T> clazz) throws JsonProcessingException {
        return Failsafe.with(idempotentRetryPolicy).get(() -> {
            HttpRequestTemplate<ByteBuf> service = builder
                    .withMethod("POST")
                    .withResponseValidator(getValidator(procedureName))
                    .withHystrixProperties(getHystrixSetter(procedureName)) //TODO maybe cache?
                    .withUriTemplate(procedureName)
                    .build();

            RibbonRequest<ByteBuf> req = service.requestBuilder().withContent(toJson(request)).build();

            ByteBuf buf = req.execute();
            return convert(clazz, buf);
        });
    }

    private HystrixCommandProperties.Setter hystrixSettings() {
        return HystrixCommandProperties.Setter()
                .withExecutionTimeoutInMilliseconds(10000)
                .withCircuitBreakerEnabled(true)
                .withCircuitBreakerSleepWindowInMilliseconds(10000)
                .withMetricsRollingStatisticalWindowInMilliseconds(10000)
                .withCircuitBreakerRequestVolumeThreshold(20)
                .withMetricsRollingPercentileEnabled(true)
                .withMetricsRollingPercentileWindowInMilliseconds(10000)
                .withMetricsRollingStatisticalWindowBuckets(10)
                .withCircuitBreakerErrorThresholdPercentage(5); //TODO set to 50
    }

    private Future<T> sendInternal(String procedureName, R request, Class<T> clazz) throws JsonProcessingException {
        HttpRequestTemplate<ByteBuf> service = builder
                .withMethod("POST")
                .withResponseValidator(getValidator(procedureName))
                .withHystrixProperties(getHystrixSetter(procedureName)) //TODO maybe cache?
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

    private HystrixObservableCommand.Setter getHystrixSetter(String procedureName) {
        return HystrixObservableCommand.Setter.
                withGroupKey(key).
                andCommandKey(HystrixCommandKey.Factory.asKey(procedureName)).
                andCommandPropertiesDefaults(hystrixSettings());
    }

    private ResponseValidator<HttpClientResponse<ByteBuf>> getValidator(final String procedureName) {
        return response -> {
            if (response.getStatus().code() > 399 && response.getStatus().code() < 500 || response.getStatus().code() < 200) {
                throw new UnsuccessfulResponseException(getMessage(procedureName, response));
            }

            if (response.getStatus().code() > 499) {
                throw new ServerError(getMessage(procedureName, response));
            }
        };
    }

    private String getMessage(String procedureName, HttpClientResponse<ByteBuf> response) {
        return "Server error with procedure name: " + procedureName + " status: " + response.getStatus().code() + " reason: " + response.getStatus().reasonPhrase();
    }

    private Observable<ByteBuf> toJson(R request) throws JsonProcessingException {
        ByteBuf buf = Unpooled.copiedBuffer(mapperDefault.writeValueAsString(request).getBytes());
        return Observable.just(buf);
    }
}
