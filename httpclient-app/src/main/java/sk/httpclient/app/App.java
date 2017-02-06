package sk.httpclient.app;


import com.netflix.client.ClientException;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public class App {
    public static void main(String[] args) throws InterruptedException, IOException, ClientException, URISyntaxException, ExecutionException {
        RibbonHttpClient<Record, Record> r = new RibbonHttpClient<>();
        Future<Record> aaa = r.send("aaa", null, Record.class);
        Record o = aaa.get();
        System.out.println(o.getCity());
    }
}
