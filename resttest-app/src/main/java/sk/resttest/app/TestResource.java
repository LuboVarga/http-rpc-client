package sk.resttest.app;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.io.InputStream;

@Path("/test")
public class TestResource {
    private final Logger LOG = LoggerFactory.getLogger(TestResource.class);

    private long sleepTime = 0;
    private boolean throwException = false;

    @GET
    @Path("/record")
    @Consumes({MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN})
    @Produces(MediaType.APPLICATION_JSON)
    public String json() {
        currentBehavior();
        return "{ \"name\":\"John\", \"age\":31, \"city\":\"Get New York\" }";
    }

    @POST
    @Path("/record")
    @Consumes({MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN})
    @Produces(MediaType.APPLICATION_JSON)
    public String json(String data) {
        System.out.println("received: " + data);
        currentBehavior();
        return "{ \"name\":\"John\", \"age\":31, \"city\":\"Post New York\" }";
    }

    /**
     * For content type discussion, have a look at http://stackoverflow.com/questions/30505408/what-is-the-correct-protobuf-content-type
     */
    @POST
    @Path("/record")
    @Consumes(MediaType.APPLICATION_OCTET_STREAM)
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public String proto(InputStream is) {
        currentBehavior();
        // TODO implement correct response https://dennis-xlc.gitbooks.io/restful-java-with-jax-rs-2-0-2rd-edition/content/en/part1/chapter6/custom_marshalling.html
        return "{ \"name\":\"John\", \"age\":31, \"city\":\"Post New York\" }";
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
                    Thread.sleep(42);
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
    private void currentBehavior() {
        if (this.sleepTime != 0) {
            try {
                Thread.sleep(this.sleepTime);
            } catch (InterruptedException e) {
                throw new RuntimeException("Interrupted in currentBehavior! ", e);
            }
        }
        if (this.throwException == true) {
            throw new RuntimeException("Some bad exception during fulfilling request happened. This should be simulation of database request failed or so.");
        }
    }
}
