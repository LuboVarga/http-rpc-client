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
import io.netty.buffer.Unpooled;
import io.reactivex.netty.protocol.http.client.HttpClientResponse;
import rx.Observable;
import rx.functions.Func1;

import java.io.IOException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class RibbonHttpClient<R, T> implements MyHttpClient<R, T> {

    private ObjectMapper mapper = new ObjectMapper();
    private HttpRequestTemplate.Builder<ByteBuf> requestBuilder;

    public RibbonHttpClient(String servers) {
        HttpResourceGroup resourceGroup = Ribbon.createHttpResourceGroup("sample-client", config(servers));
        requestBuilder = resourceGroup.newTemplateBuilder("sample-client");
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
        //clientConfig.set(CommonClientConfigKey.NFLoadBalancerClassName, "sk.httpclient.app.MyLoadBalancer");
        clientConfig.set(CommonClientConfigKey.InitializeNFLoadBalancer, true);
        clientConfig.set(CommonClientConfigKey.ListOfServers, servers);
        clientConfig.set(CommonClientConfigKey.MaxAutoRetriesNextServer, 40);
        clientConfig.set(CommonClientConfigKey.MaxAutoRetries, 40);
        clientConfig.set(CommonClientConfigKey.EnableConnectionPool, true);
        clientConfig.set(CommonClientConfigKey.PoolMaxThreads, 1500);
        clientConfig.set(CommonClientConfigKey.PoolMinThreads, 300);
        clientConfig.set(CommonClientConfigKey.PoolKeepAliveTime, 1000);
        clientConfig.set(CommonClientConfigKey.PoolKeepAliveTimeUnits, TimeUnit.SECONDS.name()); //TODO ake su tu hodnoty?
        clientConfig.set(CommonClientConfigKey.MaxConnectionsPerHost, 500);
        clientConfig.set(CommonClientConfigKey.MaxTotalConnections, 300);
        clientConfig.set(CommonClientConfigKey.EnablePrimeConnections, true);
        clientConfig.set(CommonClientConfigKey.EnablePrimeConnections, true);
        return ClientOptions.from(clientConfig);
    }


    @Override
    public Future<T> send(String procedureName, R request, Class<T> clazz) {


        ByteBuf buf = Unpooled.copiedBuffer("test content".getBytes());

        HttpRequestTemplate<ByteBuf> service = requestBuilder
                .withMethod("POST")
                .withUriTemplate("/test")
                .build();

        RibbonRequest<ByteBuf> req = service.requestBuilder().withContent(Observable.just(buf)).build();

        return req.toObservable().map(buff -> convert(clazz, buff)).toBlocking().toFuture();

    }

}
