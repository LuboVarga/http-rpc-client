package sk.httpclient.client;

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
import com.netflix.ribbon.ClientOptions;
import com.netflix.ribbon.Ribbon;
import com.netflix.ribbon.RibbonRequest;
import com.netflix.ribbon.http.HttpRequestBuilder;
import com.netflix.ribbon.http.HttpRequestTemplate;
import com.netflix.ribbon.http.HttpResourceGroup;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import sk.httpclient.RpcClient;

import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

public class RibbonHttpClient<R, T> implements RpcClient<R, T> {
    private static final Logger LOG = LoggerFactory.getLogger(RibbonHttpClient.class);

    private static final int HYSTRIX_TIMEOUT_MS = 14000;
    private static final int CLIENT_CONNECT_TIMEOUT_MS = 2000;
    private static final int CLIENT_READ_TIMEOUT_MS = 2200;

    private static final String NAME = "sample-client";
    private final ObjectMapper mapperDefault = new ObjectMapper();
    private HttpRequestTemplate.Builder<ByteBuf> builder;

    private HystrixCommandGroupKey key = HystrixCommandGroupKey.Factory.asKey(NAME);

    public RibbonHttpClient(String servers) {
        mapperDefault.registerModule(new ParameterNamesModule());
        mapperDefault.registerModule(new Jdk8Module());
        //mapperDefault.registerModule(new JavaTimeModule());
        HttpResourceGroup resourceGroup = Ribbon.createHttpResourceGroup(NAME, config(servers));
        builder = resourceGroup.newTemplateBuilder("sample-client");
    }

    private T convert(Class<T> clazz, ByteBuf buf) {
        try {
            if (clazz.equals(String.class)) {
                // copy() is solving on CompositeByteBuf instance exception "java.lang.UnsupportedOperationException: direct buffer"
                final ByteBuf byteBufCopy = buf.copy();
                final T resultFinal = (T) new String(byteBufCopy.array());
                byteBufCopy.release(); // possible leak when previous line throws an exception
                return resultFinal;
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
        } finally {
            buf.release();
        }
    }

    private ClientOptions config(String servers) {
        IClientConfig clientConfig = IClientConfig.Builder.newBuilder("sample-client").build();

        clientConfig.set(CommonClientConfigKey.NFLoadBalancerClassName, "sk.httpclient.client.LoggingLoadBalancer");
        clientConfig.set(CommonClientConfigKey.InitializeNFLoadBalancer, true);
        clientConfig.set(CommonClientConfigKey.ListOfServers, servers);
        clientConfig.set(CommonClientConfigKey.ConnectTimeout, CLIENT_CONNECT_TIMEOUT_MS);
        clientConfig.set(CommonClientConfigKey.ReadTimeout, CLIENT_READ_TIMEOUT_MS);
        final int nextServerRetry = StringUtils.countMatches(servers, ",");
        final int maxAutoRetries = 1;
        // CLIENT_CONNECT_TIMEOUT_MS is silently ignored in next equation as we expect to have pooled connection to each server
        if (HYSTRIX_TIMEOUT_MS < (CLIENT_READ_TIMEOUT_MS * maxAutoRetries) * (nextServerRetry + 1)) {
            LOG.warn("Timeouts are configured in such way, that if all tried servers will timeout, not all retries will " +
                            "be done, due to small hystrix timeout! Increase hystrix timeout, or lover retries or client read tiemout. " +
                            "Constants:{HYSTRIX_TIMEOUT_MS={};CLIENT_READ_TIMEOUT_MS={};maxAutoRetries={};nextServerRetry={}}",
                    HYSTRIX_TIMEOUT_MS, CLIENT_READ_TIMEOUT_MS, maxAutoRetries, nextServerRetry);
        }
        clientConfig.set(CommonClientConfigKey.MaxAutoRetriesNextServer, nextServerRetry);
        clientConfig.set(CommonClientConfigKey.MaxAutoRetries, maxAutoRetries);
        clientConfig.set(CommonClientConfigKey.EnableConnectionPool, true);
        clientConfig.set(CommonClientConfigKey.PoolMaxThreads, 50);
        clientConfig.set(CommonClientConfigKey.PoolMinThreads, 42);
        clientConfig.set(CommonClientConfigKey.NFLoadBalancerPingClassName, "sk.httpclient.client.LoggingPinger");
        clientConfig.set(CommonClientConfigKey.PoolKeepAliveTime, 100000000);
        clientConfig.set(CommonClientConfigKey.PoolKeepAliveTimeUnits, TimeUnit.SECONDS.toString());
        clientConfig.set(CommonClientConfigKey.MaxConnectionsPerHost, 20);
        clientConfig.set(CommonClientConfigKey.MaxTotalConnections, 70);
        clientConfig.set(CommonClientConfigKey.EnablePrimeConnections, true);
        clientConfig.set(CommonClientConfigKey.EnablePrimeConnections, true);
        return ClientOptions.from(clientConfig);
    }

    public T sendCommand(String procedureName, R request, Class<T> clazz) throws JsonProcessingException, ExecutionException, InterruptedException {
        return sendPost(procedureName, request, clazz);
    }

    @Override
    public T sendQuery(String procedureName, Getable request, Class<T> clazz) throws JsonProcessingException, ExecutionException, InterruptedException {
        return sendGet(procedureName, request, clazz);
    }

    private HystrixCommandProperties.Setter hystrixSettings() {
        return HystrixCommandProperties.Setter()
                .withExecutionTimeoutInMilliseconds(HYSTRIX_TIMEOUT_MS)
                .withCircuitBreakerEnabled(true)
                .withCircuitBreakerSleepWindowInMilliseconds(10000)
                .withMetricsRollingStatisticalWindowInMilliseconds(10000)
                .withCircuitBreakerRequestVolumeThreshold(20)
                .withMetricsRollingPercentileEnabled(true)
                .withMetricsRollingPercentileWindowInMilliseconds(10000)
                .withMetricsRollingStatisticalWindowBuckets(10)
                .withCircuitBreakerErrorThresholdPercentage(50)
                .withMetricsHealthSnapshotIntervalInMilliseconds(1000); // to have metrics refreshed http://stackoverflow.com/a/34799087/6034197
    }

    private T sendPost(String procedureName, R request, Class<T> clazz) throws JsonProcessingException {
        HttpRequestTemplate<ByteBuf> service = builder
                .withMethod("POST")
                .withResponseValidator(new Validator(procedureName))
                .withHystrixProperties(getHystrixSetter(procedureName)) //TODO maybe cache?
                .withUriTemplate(procedureName)
                .build();

        RibbonRequest<ByteBuf> req = service.requestBuilder().withContent(toJson(request)).build();
        return convert(clazz, req.execute());
    }

    private T sendGet(String procedureName, Getable request, Class<T> clazz) {
        HttpRequestTemplate<ByteBuf> service = builder
                .withMethod("GET")
                .withResponseValidator(new Validator(procedureName))
                .withHystrixProperties(getHystrixSetter(procedureName)) //TODO maybe cache?
                .withUriTemplate(procedureName)
                .build();

        HttpRequestBuilder<ByteBuf> req = service.requestBuilder();
        addParams(req, request.toMap());

        return convert(clazz, req.build().execute());
    }

    private void addParams(HttpRequestBuilder<ByteBuf> req, Map<String, String> stringStringMap) {
        stringStringMap.forEach(req::withRequestProperty);
    }

    private HystrixObservableCommand.Setter getHystrixSetter(String procedureName) {
        return HystrixObservableCommand.Setter.
                withGroupKey(key).
                andCommandKey(HystrixCommandKey.Factory.asKey(procedureName)).
                andCommandPropertiesDefaults(hystrixSettings());
    }


    private Observable<ByteBuf> toJson(R request) throws JsonProcessingException {
        ByteBuf buf = Unpooled.copiedBuffer(mapperDefault.writeValueAsString(request).getBytes());
        return Observable.just(buf);
    }
}
