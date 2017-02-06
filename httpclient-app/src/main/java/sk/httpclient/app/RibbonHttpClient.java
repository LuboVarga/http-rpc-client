package sk.httpclient.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.client.config.CommonClientConfigKey;
import com.netflix.client.config.IClientConfig;
import com.netflix.ribbon.ClientOptions;
import com.netflix.ribbon.Ribbon;
import com.netflix.ribbon.RibbonRequest;
import com.netflix.ribbon.http.HttpRequestTemplate;
import com.netflix.ribbon.http.HttpResourceGroup;
import com.netflix.ribbon.transport.netty.RibbonTransport;
import com.netflix.ribbon.transport.netty.http.LoadBalancingHttpClient;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.HttpMethod;
import io.reactivex.netty.protocol.http.client.HttpClientRequest;
import io.reactivex.netty.protocol.http.client.HttpClientResponse;
import rx.Observable;
import rx.functions.Func1;

import java.io.IOException;
import java.util.concurrent.Future;

public class RibbonHttpClient<R, T> implements MyHttpClient<R, T> {

    ObjectMapper mapper = new ObjectMapper();


    public Future<T> singleSend(String procedureName, R request, Class<T> clazz) {
        try {
            HttpClientRequest<ByteBuf> req = HttpClientRequest.create(HttpMethod.GET, "http://localhost:8888/test");
            LoadBalancingHttpClient<ByteBuf, ByteBuf> client = getClient();
            Observable<HttpClientResponse<ByteBuf>> observable = client.submit(req);
            return observable.flatMap(getContent(clazz)).toBlocking().toFuture();

        } catch (IOException e) {
            e.printStackTrace();
        }
        System.out.println("koniec");
        return null;
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


    public LoadBalancingHttpClient<ByteBuf, ByteBuf> getClient() throws IOException {
        IClientConfig clientConfig = mySecondConfig();
        return RibbonTransport.newHttpClient(clientConfig);
    }

    private IClientConfig mySecondConfig() {
        IClientConfig clientConfig = IClientConfig.Builder.newBuilder("sample-client").build();
        clientConfig.set(CommonClientConfigKey.NFLoadBalancerClassName, "sk.httpclient.app.MyLoadBalancer");
        clientConfig.set(CommonClientConfigKey.InitializeNFLoadBalancer, true);
        clientConfig.set(CommonClientConfigKey.ListOfServers, "http://localhost:8888, http://localhost:8889, http://localhost:8887");
        clientConfig.set(CommonClientConfigKey.MaxAutoRetriesNextServer, 4);
        clientConfig.set(CommonClientConfigKey.MaxAutoRetries, 4);
        return clientConfig;
    }


    @Override
    public Future<T> send(String procedureName, R request, Class<T> clazz) {
        HttpResourceGroup resourceGroup = Ribbon.createHttpResourceGroup("sample-client", getMyConfig());

        HttpRequestTemplate.Builder<ByteBuf> builder = resourceGroup.newTemplateBuilder("sample-client");
        HttpRequestTemplate<ByteBuf> recommendationsByUserIdTemplate = builder
                .withMethod("GET")
                .withUriTemplate("/test")
                .build();

        RibbonRequest<ByteBuf> req = recommendationsByUserIdTemplate.requestBuilder().build();

        return req.toObservable().map(buff -> convert(clazz, buff)).toBlocking().toFuture();

    }

    private ClientOptions getMyConfig() {
        return ClientOptions.from(mySecondConfig());
    }

}
