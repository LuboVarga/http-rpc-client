package sk.httpclient.app;


import com.codahale.metrics.ConsoleReporter;
import com.codahale.metrics.MetricRegistry;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.netflix.hystrix.contrib.codahalemetricspublisher.HystrixCodaHaleMetricsPublisher;
import com.netflix.hystrix.exception.HystrixRuntimeException;
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
            client.sendIdempotentImmidiate("/test/control", "fail 500", Record.class);
        } catch (Throwable e) {
            e.printStackTrace();
        }

        int accumulator = makeIdempotentRequests(120, 100);

        assertEquals(120, accumulator);
    }

    @Test(expected = HystrixRuntimeException.class)
    public void multipleServerBadService() throws JsonProcessingException, ExecutionException, InterruptedException {
        try {
            client.sendIdempotentImmidiate("/test/control", "fail 500", Record.class);
            client.sendIdempotentImmidiate("/test/control", "fail 500", Record.class);
        } catch (Throwable e) {
            e.printStackTrace();
        }

        makeNonIdempotentRequests(120, 100);
    }

    private int makeIdempotentRequests(int requestCount, int delay) throws InterruptedException, JsonProcessingException {
        int accumulator = 0;
        Record ok;

        for (int i = 0; i < requestCount; i++) {
            ok = client.sendIdempotentImmidiate("/test/maybefail", "1", Record.class);
            Thread.sleep(delay);
            if (ok != null)
                accumulator += ok.getAge();
        }
        return accumulator;
    }

   private int makeNonIdempotentRequests(int requestCount, int delay) throws InterruptedException, JsonProcessingException {
        int accumulator = 0;
        Record ok;

        for (int i = 0; i < requestCount; i++) {
            ok = client.sendNonIdempotentImmidiate("/test/maybefail", "1", Record.class);
            Thread.sleep(delay);
            if (ok != null)
                accumulator += ok.getAge();
        }
        return accumulator;
    }

}
