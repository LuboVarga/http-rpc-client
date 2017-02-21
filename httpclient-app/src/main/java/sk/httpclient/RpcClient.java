package sk.httpclient;

import com.fasterxml.jackson.core.JsonProcessingException;
import sk.httpclient.client.Getable;

import java.util.concurrent.ExecutionException;

public interface RpcClient<R, T> {
    T sendQuery(String procedureName, Getable request, Class<T> clazz) throws JsonProcessingException, ExecutionException, InterruptedException;

    T sendCommand(String procedureName, R request, Class<T> clazz) throws JsonProcessingException, ExecutionException, InterruptedException;
}
