package sk.httpclient.app;


import java.util.concurrent.Future;

public interface MyHttpClient<R, T> {

    Future<T> send(String procedureName, R request, Class<T> clazz);

}
