package sk.httpclient.app;


import com.google.common.collect.Lists;
import com.netflix.client.ClientException;
import com.netflix.loadbalancer.Server;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public class App {
    public static void main(String[] args) throws InterruptedException, IOException, ClientException, URISyntaxException, ExecutionException {
//        RibbonHttpClient<Record, Record> r = new RibbonHttpClient<>("http://localhost:8888, http://localhost:8889, http://localhost:8887");
//        for (int i = 0; i < 200; i++) {
//            long startTime = System.nanoTime();
//            Future<Record> aaa = r.send("/test", null, Record.class);
//            Record o = aaa.get();
//            long endTime = System.nanoTime();
//            System.out.println("time:" + (endTime - startTime) + " data: " + o.getCity());
//            Thread.sleep(50);
//
//        }

        ArrayList<Server> servers = Lists.newArrayList(
                new Server("localhost", 8886),
                new Server("localhost", 8887),
                new Server("localhost", 8881));

        ExperimentalClient<String, Record> client = new ExperimentalClient(servers);

        for (int i = 0; i < 10; i++) {
            Record record = null;
            try {
                Future<Record> send = client.send("/test/test", null, Record.class);
                record = send.get();
            } catch (ExecutionException | InterruptedException e) {
            }
            System.out.println("record: " + (record == null ? "kakanica" : record.getCity()));
        }

        System.exit(0);
    }
}
