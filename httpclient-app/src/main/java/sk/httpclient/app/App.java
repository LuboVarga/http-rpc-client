package sk.httpclient.app;

import com.codahale.metrics.ConsoleReporter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import com.netflix.client.ClientException;
import com.netflix.hystrix.contrib.codahalemetricspublisher.HystrixCodaHaleMetricsPublisher;
import com.netflix.hystrix.strategy.HystrixPlugins;
import org.apache.commons.math3.stat.descriptive.SynchronizedSummaryStatistics;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.concurrent.*;

public class App {
    private static final boolean DOSLEEP = false;
    private static final int REQUESTSINSINGLERUN = 123;
    private static final int NUMBEROFGENERATEDTASKS = 4;

    public static void main(String[] args) throws InterruptedException, IOException, ClientException, URISyntaxException, ExecutionException {
        long startTotal = System.currentTimeMillis();
        RibbonHttpClient<Record, Record> r = new RibbonHttpClient<>("http://localhost:8888, http://localhost:8889, http://localhost:8887");
        SynchronizedSummaryStatistics s = new SynchronizedSummaryStatistics();
        MetricRegistry metricRegistry = new MetricRegistry();
        HystrixPlugins.getInstance().registerMetricsPublisher(new HystrixCodaHaleMetricsPublisher(metricRegistry));
        Timer timer = metricRegistry.timer("http-timer");
        ConsoleReporter reporter = ConsoleReporter.forRegistry(metricRegistry)
                .convertRatesTo(TimeUnit.SECONDS)
                .convertDurationsTo(TimeUnit.MILLISECONDS)
                .build();
        reporter.start(10, TimeUnit.SECONDS);

        Runnable runnable = () -> {
            try {
                runRequests(r, timer, s);
            } catch (ExecutionException | InterruptedException e) {
                System.out.println("Error in requests. ex=" + e.getMessage());
            }
        };

        ExecutorService executor = Executors.newFixedThreadPool(20);

        for (int i = 0; i < NUMBEROFGENERATEDTASKS; i++) {
            executor.execute(runnable);
        }

        executor.shutdown();
        System.out.println("terminated OK=" + executor.awaitTermination(100, TimeUnit.SECONDS));
        System.out.println("Statistics=" + s);
        reporter.report();
        long totalDutation = System.currentTimeMillis() - startTotal;
        System.out.println("Total duration (wall clock) = " + totalDutation + "ms. \"utilization\"=" + (s.getSum() / totalDutation) + "");
        System.exit(0);
    }

    private static void runRequests(RibbonHttpClient<Record, Record> r, Timer timer, SynchronizedSummaryStatistics s) throws InterruptedException, ExecutionException {
        for (int i = 0; i < REQUESTSINSINGLERUN; i++) {
            long start = System.nanoTime();
            try (Timer.Context ctx = timer.time()) {
                r.sendIdempotentImmidiate("/test/record?age={age}", new Record(), Record.class);
            } catch (Exception ex) {
                System.err.println("RPC call failed. ex=" + ex.getMessage());
                ex.printStackTrace();
            }
            s.addValue((System.nanoTime() - start) / 1000000.0);
            if (DOSLEEP) {
                Thread.sleep(50);
            }
        }
    }
}
