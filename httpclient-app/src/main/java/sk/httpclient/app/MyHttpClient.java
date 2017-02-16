package sk.httpclient.app;

import com.fasterxml.jackson.core.JsonProcessingException;

import java.util.concurrent.Future;

public interface MyHttpClient<R, T> {
    Future<T> send(String procedureName, R request, Class<T> clazz) throws JsonProcessingException;
    Future<T> sendIdempotent(String procedureName, R request, Class<T> clazz) throws JsonProcessingException;
    T sendIdempotentImmidiate(String procedureName, R request, Class<T> clazz) throws JsonProcessingException;
}
