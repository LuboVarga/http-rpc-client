package sk.httpclient.app;


import com.codahale.metrics.ConsoleReporter;
import com.codahale.metrics.MetricRegistry;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.netflix.hystrix.contrib.codahalemetricspublisher.HystrixCodaHaleMetricsPublisher;
import com.netflix.hystrix.strategy.HystrixPlugins;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;

@Ignore
public class ServiceErrorTests {

    static RibbonHttpClient<String, Record> client;

    @BeforeClass
    public static void aaa() {
        client = new RibbonHttpClient<>("http://localhost:8888, http://localhost:8889, http://localhost:8887");
        MetricRegistry metricRegistry = new MetricRegistry();
        HystrixPlugins.getInstance().registerMetricsPublisher(new HystrixCodaHaleMetricsPublisher(metricRegistry));
        ConsoleReporter reporter = ConsoleReporter.forRegistry(metricRegistry)
                .convertRatesTo(TimeUnit.SECONDS)
                .convertDurationsTo(TimeUnit.MILLISECONDS)
                .build();
        reporter.start(10, TimeUnit.SECONDS);

    }

    @Test
    public void atLeast1Server() throws JsonProcessingException, ExecutionException, InterruptedException {
        try {
            client.sendIdempotent("/test/maybefail", "ok", Record.class).get();
        } catch (Throwable e) {
            e.printStackTrace();
        }

        int accumulator = 0;
        for (int i = 0; i < 10; i++) {
            Record ok = client.sendIdempotent("/test/maybefail", "1", Record.class).get();
            accumulator += ok.getAge();
        }

        assertEquals(10, accumulator);
    }

    @Test
    public void oneServerBadService() throws JsonProcessingException, ExecutionException, InterruptedException {
        try {
            client.sendIdempotentImmidiate("/test/control", "5", Record.class);
        } catch (Throwable e) {
            e.printStackTrace();
        }

        int accumulator = makeRequests();

        assertEquals(10, accumulator);
    }

    @Test
    public void omultipleServerBadService() throws JsonProcessingException, ExecutionException, InterruptedException {
        try {
            client.sendIdempotentImmidiate("/test/control", "5", Record.class);
            client.sendIdempotentImmidiate("/test/control", "5", Record.class);
        } catch (Throwable e) {
            e.printStackTrace();
        }

        int accumulator = makeRequests();

        assertEquals(10, accumulator);
    }

    private int makeRequests() {
        int accumulator = 0;
        for (int i = 0; i < 600; i++) {
            Record ok = null;
            System.out.println("iteracia: " + i);
            try {
                ok = client.sendIdempotentImmidiate("/test/maybefail", "1", Record.class);
                Thread.sleep(12);
            } catch (Throwable e) {
                System.out.println("vynimka " + e.getClass().getName());
                e.printStackTrace();
            }
            if (ok != null)
                accumulator += ok.getAge();
        }
        return accumulator;
    }

}
