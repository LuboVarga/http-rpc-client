package sk.httpclient.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.client.config.CommonClientConfigKey;
import com.netflix.client.config.IClientConfig;
import com.netflix.ribbon.ClientOptions;
import com.netflix.ribbon.Ribbon;
import com.netflix.ribbon.RibbonRequest;
import com.netflix.ribbon.http.HttpRequestTemplate;
import com.netflix.ribbon.http.HttpResourceGroup;
import io.netty.buffer.ByteBuf;
import io.reactivex.netty.protocol.http.client.HttpClientResponse;
import rx.Observable;
import rx.functions.Func1;

import java.io.IOException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class RibbonHttpClient<R, T> implements MyHttpClient<R, T> {

    private ObjectMapper mapper = new ObjectMapper();
    private HttpResourceGroup resourceGroup;


    public RibbonHttpClient(String servers) {
        resourceGroup = Ribbon.createHttpResourceGroup("sample-client", config(servers));
    }

    private Func1<HttpClientResponse<ByteBuf>, Observable<T>> getContent(Class<T> clazz) {
        return x -> x.getContent().map(data -> convert(clazz, data));
    }

    private T convert(Class<T> clazz, ByteBuf buf) {
        try {
            byte[] bytes = new byte[buf.readableBytes()];
            buf.readBytes(bytes);
            return mapper.readValue(bytes, clazz);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    private ClientOptions config(String servers) {
        IClientConfig clientConfig = IClientConfig.Builder.newBuilder("sample-client").build();
        clientConfig.set(CommonClientConfigKey.NFLoadBalancerClassName, "sk.httpclient.app.MyLoadBalancer");
        clientConfig.set(CommonClientConfigKey.InitializeNFLoadBalancer, true);
        clientConfig.set(CommonClientConfigKey.ListOfServers, servers);
        clientConfig.set(CommonClientConfigKey.MaxAutoRetriesNextServer, 4);
        clientConfig.set(CommonClientConfigKey.MaxAutoRetries, 4);
        clientConfig.set(CommonClientConfigKey.EnableConnectionPool, true);
        clientConfig.set(CommonClientConfigKey.PoolMaxThreads, 5);
        clientConfig.set(CommonClientConfigKey.PoolMinThreads, 3);
        clientConfig.set(CommonClientConfigKey.PoolKeepAliveTime, 1000);
        clientConfig.set(CommonClientConfigKey.PoolKeepAliveTimeUnits, TimeUnit.SECONDS.name()); //TODO ake su tu hodnoty?
        clientConfig.set(CommonClientConfigKey.MaxConnectionsPerHost, 5);
        clientConfig.set(CommonClientConfigKey.MaxTotalConnections, 30);
        clientConfig.set(CommonClientConfigKey.EnablePrimeConnections, true);
        clientConfig.set(CommonClientConfigKey.EnablePrimeConnections, true);
        return ClientOptions.from(clientConfig);
    }


    @Override
    public Future<T> send(String procedureName, R request, Class<T> clazz) {

        HttpRequestTemplate.Builder<ByteBuf> builder = resourceGroup.newTemplateBuilder("sample-client");
        HttpRequestTemplate<ByteBuf> service = builder
                .withMethod("GET")
                .withUriTemplate("/test")
                .build();

        RibbonRequest<ByteBuf> req = service.requestBuilder().build();

        return req.toObservable().map(buff -> convert(clazz, buff)).toBlocking().toFuture();

    }

}
