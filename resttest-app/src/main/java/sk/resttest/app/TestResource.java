package sk.resttest.app;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.io.InputStream;

@Path("/test")
public class TestResource {
    @GET
    @Path("/record")
    @Consumes({MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN})
    @Produces(MediaType.APPLICATION_JSON)
    public String json() {
        return "{ \"name\":\"John\", \"age\":31, \"city\":\"Get New York\" }";
    }

    @POST
    @Path("/record")
    @Consumes({MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN})
    @Produces(MediaType.APPLICATION_JSON)
    public String json(String data) {
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
        // TODO implement correct response https://dennis-xlc.gitbooks.io/restful-java-with-jax-rs-2-0-2rd-edition/content/en/part1/chapter6/custom_marshalling.html
        return "{ \"name\":\"John\", \"age\":31, \"city\":\"Post New York\" }";
    }
}
