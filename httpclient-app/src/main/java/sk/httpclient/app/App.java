package sk.httpclient.app;


import com.codahale.metrics.ConsoleReporter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import com.netflix.client.ClientException;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.concurrent.*;

public class App {
    public static void main(String[] args) throws InterruptedException, IOException, ClientException, URISyntaxException, ExecutionException {
        RibbonHttpClient<Record, Record> r = new RibbonHttpClient<>("http://localhost:8888, http://localhost:8889, http://localhost:8887");
        MetricRegistry metricRegistry = new MetricRegistry();
        Timer timer = metricRegistry.timer("http-timer");
        ConsoleReporter reporter = ConsoleReporter.forRegistry(metricRegistry)
                .convertRatesTo(TimeUnit.SECONDS)
                .convertDurationsTo(TimeUnit.MILLISECONDS)
                .build();
        reporter.start(300, TimeUnit.MILLISECONDS);

        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                try {
                    runRequests(r, timer);
                } catch (ExecutionException | InterruptedException e) {
                    e.printStackTrace();
                }
            }
        };

        ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(100);

        for (int i = 0; i < 3; i++) {
            executor.execute(runnable);
        }

//        ArrayList<Server> servers = Lists.newArrayList(
//                new Server("localhost", 8886),
//                new Server("localhost", 8887),
//                new Server("localhost", 8881));
//
//        ExperimentalClient<String, Record> client = new ExperimentalClient(servers);
//
//        for (int i = 0; i < 10; i++) {
//            Record record = null;
//            try {
//                Future<Record> send = client.send("/test/test", null, Record.class);
//                record = send.get();
//            } catch (ExecutionException | InterruptedException e) {
//            }
//            System.out.println("record: " + (record == null ? "kakanica" : record.getCity()));
//        }
//

        executor.shutdown();
        executor.awaitTermination(100, TimeUnit.DAYS);
        System.exit(0);
    }

    private static void runRequests(RibbonHttpClient<Record, Record> r, Timer timer) throws InterruptedException, ExecutionException {

        for (int i = 0; i < 500; i++) {
            try (Timer.Context ctx = timer.time()) {
                //  long startTime = System.nanoTime();
                Future<Record> aaa = r.send("/test", null, Record.class);
                Record o = aaa.get();
                //long endTime = System.nanoTime();
                //System.out.print(".");
                //   System.out.println("time:" + (endTime - startTime) / 1000000.0 + " data: " + o.getCity());
            }
            Thread.sleep(50);
        }
    }
}
