package sk.resttest.app;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicInteger;

@Path("/test")
public class TestResource {
    private static final Logger LOG = LoggerFactory.getLogger(TestResource.class);
    private static AtomicInteger recordPostCount = new AtomicInteger(0);
    private static AtomicInteger failCode = new AtomicInteger(0);
    private static long sleepTime = 0;
    private static boolean throwException = false;

    @GET
    @Path("/record")
    @Produces(MediaType.APPLICATION_JSON)
    public String json() {
        LOG.debug("GET  on /test/record");
        currentRecordBehavior();
        return "{ \"name\":\"John\", \"age\":31, \"city\":\"Get New York\" }";
    }

    @POST
    @Path("/record")
    @Consumes({MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN})
    @Produces(MediaType.APPLICATION_JSON)
    public String json(String data) {
        LOG.debug("POST on /test/record. recordPostCount={}, data={}.", recordPostCount.incrementAndGet(), data);
        currentRecordBehavior();
        return data;
    }

    @POST
    @Path("/maybefail")
    @Consumes({MediaType.TEXT_PLAIN})
    @Produces(MediaType.APPLICATION_JSON)
    public Response maybeFail(String data) {
        int returnCode = failCode.get();
        if (returnCode != 0) {
            System.out.print(" (" + returnCode + ") ");
            return Response.status(returnCode).build();
        } else {
            System.out.print("." + data + ".");
            return Response.ok().entity("{ \"name\":\"John\", \"age\":" + data + ", \"city\":\"Post New York\" }").build();
        }
    }

    @GET
    @Path("/maybefail")
    @Consumes({MediaType.TEXT_PLAIN})
    @Produces(MediaType.APPLICATION_JSON)
    public Response maybeFail() {
        int returnCode = failCode.get();
        if (returnCode != 0) {
            System.out.print(" (" + returnCode + ") ");
            return Response.status(returnCode).build();
        } else {
            System.out.print("." + 1 + ".");
            return Response.ok().entity("{ \"name\":\"John\", \"age\":" + 1 + ", \"city\":\"Post New York\" }").build();
        }
    }

    /**
     * For content type discussion, have a look at http://stackoverflow.com/questions/30505408/what-is-the-correct-protobuf-content-type
     */
    @POST
    @Path("/record")
    @Consumes(MediaType.APPLICATION_OCTET_STREAM)
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public String proto(InputStream is) {
        LOG.debug("POST on /test/record. recordPostCount={}, data=inputstream.", recordPostCount.incrementAndGet());
        currentRecordBehavior();
        // TODO implement correct response https://dennis-xlc.gitbooks.io/restful-java-with-jax-rs-2-0-2rd-edition/content/en/part1/chapter6/custom_marshalling.html
        return "{ \"name\":\"John\", \"age\":31, \"city\":\"Post New York\" }";
    }

    @POST
    @Path("/call")
    @Consumes({MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN})
    @Produces(MediaType.APPLICATION_JSON)
    public String jsonCall(String data) {
        LOG.debug("POST on /test/call. data={}.", data);
        return data;
    }

    @GET
    @Path("/call")
    @Produces(MediaType.APPLICATION_JSON)
    public String jsonCall() {
        LOG.debug("GET  on /test/call");
        return "{ \"name\":\"Cyril\", \"age\":22, \"city\":\"soap call\" }";
    }

    @GET
    @Path("/control")
    @Produces(MediaType.APPLICATION_JSON)
    public String controlGet(String data) {
        LOG.debug("GET  on /test/control. data={}.", data);
        return this.control(data);
    }

    @POST
    @Path("/control")
    @Consumes({MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN, MediaType.APPLICATION_OCTET_STREAM})
    @Produces(MediaType.APPLICATION_JSON)
    public String controlPost(String data) {
        LOG.debug("POST on /test/control. data={}.", data);
        return this.control(data);
    }

    /**
     * @param data seems to come from client something like "\"1,12\"" for example.
     * @return
     */
    public String control(String data) {
        LOG.debug("\tcontrol /test/control. data={}.", data);
        if (data.startsWith("\"fail")) {
            Integer errorCode = Integer.valueOf(data.substring(1, data.length() - 1).split(" ")[1]);
            failCode.set(errorCode);
            LOG.info("\tcontrol have set to fail with errorCode={}.", errorCode);
            return "{}";
        }
        if (data.startsWith("\"4")) {
            this.sleepTime = 0;
            this.throwException = false;
            failCode.set(0);
            LOG.info("control is going to 4. All ok state.");
            return "{ \"name\":\"XXX\", \"age\":31, \"city\":\"ALL OK\" }";
        }
        if (data.startsWith("\"3")) {
            System.out.println("data.startsWith(\"3\"):" + data);
            String[] splitted = data.split(",");
            long timeoutSleep = Long.parseLong(splitted[1].replace("\"", ""));

            this.sleepTime = timeoutSleep;
            LOG.info("control is going to 3. Overload state. Sleep time in ms:{}.", timeoutSleep);
            return "{ \"name\":\"XXX\", \"age\":" + timeoutSleep + ", \"city\":\"TIMEOUT SIMULATION\" }";
        }
        if (data.startsWith("\"2")) {
            this.throwException = true;
            LOG.info("control is going to 2. DB down state.");
            return "{ \"name\":\"XXX\", \"age\":31, \"city\":\"PROCESSING EXCEPTION SIMULATION\" }";
        }
        if (data.startsWith("\"1")) {
            String[] splitted = data.split(",");
            int deploySleep = Integer.parseInt(splitted[1].replace("\"", ""));
            LOG.info("control is going to 1. Restart (simulated deploy) state. Restart seconds:{}.", deploySleep);
            new Thread(() -> {
                try {
                    Thread.sleep(42);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                System.exit(deploySleep);
            }).start();
            return "{ \"name\":\"XXX\", \"age\":" + deploySleep + ", \"city\":\"DEPLOY SIMULATION\" }";
        }
        return "{ \"name\":\"XXX\", \"age\":31, \"city\":\"XX!!XX!!XX\" }";
    }

    /**
     * Optionally make sleep (simulates service overload) and than optionally throw exception (simulates database down).
     */
    private void currentRecordBehavior() {
        if (this.sleepTime != 0) {
            LOG.trace("this.sleepTime={}.", this.sleepTime);
            try {
                Thread.sleep(this.sleepTime);
            } catch (InterruptedException e) {
                LOG.warn("dummy sleep was interrupted.");
                throw new RuntimeException("Interrupted in currentRecordBehavior! ", e);
            }
        }
        if (this.throwException == true) {
            LOG.trace("this.throwException={}.", this.throwException);
            throw new RuntimeException("Some bad exception during fulfilling request happened. This should be simulation of database request failed or so.");
        }
    }
}
