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
    private final Logger LOG = LoggerFactory.getLogger(TestResource.class);
    static AtomicInteger counter = new AtomicInteger(0);
    static boolean fail = false;
    private long sleepTime = 0;
    private boolean throwException = false;

    @GET
    @Path("/record")
    @Consumes({MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN})
    @Produces(MediaType.APPLICATION_JSON)
    public String json() {
        System.out.println("received: get");

        currentRecordBehavior();
        return "{ \"name\":\"John\", \"age\":31, \"city\":\"Get New York\" }";
    }

    @POST
    @Path("/record")
    @Consumes({MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN})
    @Produces(MediaType.APPLICATION_JSON)
    public String json(String data) {
        System.out.println("received: " + data + " count: " + counter.incrementAndGet());
        currentRecordBehavior();
        return "{ \"name\":\"John\", \"age\":31, \"city\":\"Post New York\" }";
    }

    @POST
    @Path("/maybefail")
    @Consumes({MediaType.TEXT_PLAIN})
    @Produces(MediaType.APPLICATION_JSON)
    public Response maybeFail(String data) {
        if (fail) {
            System.out.print(" (404 " + TestResource.fail + ") ");

            return Response.status(Response.Status.FORBIDDEN).build();
        } else {
            System.out.print("." + data + " " + TestResource.fail + ".");
            return Response.ok().entity("{ \"name\":\"John\", \"age\":" + data + ", \"city\":\"Post New York\" }").build();
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
        System.out.println("received: octet");

        currentRecordBehavior();
        // TODO implement correct response https://dennis-xlc.gitbooks.io/restful-java-with-jax-rs-2-0-2rd-edition/content/en/part1/chapter6/custom_marshalling.html
        return "{ \"name\":\"John\", \"age\":31, \"city\":\"Post New York\" }";
    }


    @POST
    @Path("/call")
    @Consumes({MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN})
    @Produces(MediaType.APPLICATION_JSON)
    public String jsonCall(String data) {
        System.out.println("received call: " + data);
        return "{ \"name\":\"Cyril\", \"age\":22, \"city\":\"soap call\" }";
    }

    @GET
    @Path("/control")
    @Consumes({MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN, MediaType.APPLICATION_OCTET_STREAM})
    @Produces(MediaType.APPLICATION_JSON)
    public String controlGet(String data) {
        return this.control(data);
    }

    @POST
    @Path("/control")
    @Consumes({MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN, MediaType.APPLICATION_OCTET_STREAM})
    @Produces(MediaType.APPLICATION_JSON)
    public String controlPost(String data) {
        return this.control(data);
    }

    public String control(String data) {
        if (data.contains("5")) {
            this.sleepTime = 0;
            this.throwException = false;
            fail = true;
            LOG.info("control is going to 4. All ok state.");
            return "{ \"name\":\"XXX\", \"age\":31, \"city\":\"ALL OK\" }";
        }
        if (data.contains("4")) {
            this.sleepTime = 0;
            this.throwException = false;
            LOG.info("control is going to 4. All ok state.");
            return "{ \"name\":\"XXX\", \"age\":31, \"city\":\"ALL OK\" }";
        }
        if (data.contains("3")) {
            this.sleepTime = 18000;
            LOG.info("control is going to 3. Overload state.");
            return "{ \"name\":\"XXX\", \"age\":31, \"city\":\"TIMEOUT SIMULATION\" }";
        }
        if (data.contains("2")) {
            this.throwException = true;
            LOG.info("control is going to 2. DB down state.");
            return "{ \"name\":\"XXX\", \"age\":31, \"city\":\"PROCESSING EXCEPTION SIMULATION\" }";
        }
        if (data.contains("1")) {
            LOG.info("control is going to 1. Restart (simulated deploy) state.");
            new Thread(() -> {
                try {
                    Thread.sleep(242);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                System.exit(91);
            }).start();
            return "{ \"name\":\"XXX\", \"age\":31, \"city\":\"DEPLOY SIMULATION\" }";
        }
        return "{ \"name\":\"XXX\", \"age\":31, \"city\":\"XX!!XX!!XX\" }";
    }

    /**
     * Optionally make sleep (simulates service overload) and than optionally throw exception (simulates database down).
     */
    private void currentRecordBehavior() {
        if (this.sleepTime != 0) {
            System.out.println("this.sleepTime=" + this.sleepTime);
            try {
                Thread.sleep(this.sleepTime);
            } catch (InterruptedException e) {
                throw new RuntimeException("Interrupted in currentRecordBehavior! ", e);
            }
        }
        if (this.throwException == true) {
            System.out.println("this.throwException=" + this.throwException);
            throw new RuntimeException("Some bad exception during fulfilling request happened. This should be simulation of database request failed or so.");
        }
    }
}
