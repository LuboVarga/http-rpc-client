package sk.httpclient.app;


import com.codahale.metrics.ConsoleReporter;
import com.codahale.metrics.MetricRegistry;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.netflix.hystrix.contrib.codahalemetricspublisher.HystrixCodaHaleMetricsPublisher;
import com.netflix.hystrix.exception.HystrixBadRequestException;
import com.netflix.hystrix.exception.HystrixRuntimeException;
import com.netflix.hystrix.strategy.HystrixPlugins;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

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

    /**
     * 1 server from server list working, others return 500. All requests are idempotent and should go to this 1 remaining server.
     */

    @Test
    public void remaining1ServerIdempotent() throws JsonProcessingException, ExecutionException, InterruptedException {
        clearAllflags();
        disableServer(2, "fail 500");

        int accumulator = 0;
        for (int i = 0; i < 10; i++) {
            Record ok = client.sendIdempotentImmidiate("/test/maybefail", "1", Record.class);
            accumulator += ok.getAge();
        }

        assertEquals(10, accumulator);
    }

    private void sendControl(String cmd) throws JsonProcessingException {
        client.sendNonIdempotentImmidiate("/test/control", cmd, Record.class);
    }

    /**
     * 1 server from server list working, others return 500. Sending non-idempotent requests which fail 66% of time.
     * Eventually this opens the Circuit breaker
     */

    @Test
    public void remaining1ServerNonIdempotent() throws JsonProcessingException, ExecutionException, InterruptedException {
        clearAllflags();
        disableServer(2, "fail 500");
        boolean shortcircuit = false;
        boolean badRequestException = false;

        for (int i = 0; i < 100; i++) {
            try {
                makeNonIdempotentRequests(120, 10);
            } catch (HystrixRuntimeException e) {
                if (e.getFailureType().equals(HystrixRuntimeException.FailureType.COMMAND_EXCEPTION))
                    badRequestException = true;
                if (e.getFailureType().equals(HystrixRuntimeException.FailureType.SHORTCIRCUIT))
                    shortcircuit = true;
            }
        }

        assertTrue(shortcircuit);
        assertTrue(badRequestException);

    }

    /**
     * 1 server from server list working, others return 404. Requests fail with an exception bacese it's a 4XX response.
     */
    @Test(expected = HystrixBadRequestException.class)
    public void noServerWorkingIdempotent() throws JsonProcessingException, ExecutionException, InterruptedException {
        clearAllflags();
        disableServer(3, "fail 404");

        makeIdempotentRequests(10, 10);
    }

    private void disableServer(int times, String cmd) throws JsonProcessingException {
        for (int i = 0; i < times; i++) {
            sendControl(cmd);
        }
    }

    /**
     * 1 server from server list working, others return 404. Requests fail with an exception bacese it's a 4XX response.
     */
    @Test(expected = HystrixBadRequestException.class)
    public void noServerWorkingNonIdempotent() throws JsonProcessingException, ExecutionException, InterruptedException {
        clearAllflags();
        disableServer(3, "fail 404");

        makeNonIdempotentRequests(10, 10);
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

    private void clearAllflags() throws InterruptedException, JsonProcessingException {
        for (int i = 0; i < 50; i++) {
            sendControl("4");
        }
    }

}
