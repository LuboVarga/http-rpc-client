package sk.resttest.app;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;

@Path("/problems")
public class ExitResource {
    @GET
    @Path("/systemExit")
    @Consumes({MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN, MediaType.APPLICATION_OCTET_STREAM})
    @Produces(MediaType.APPLICATION_JSON)
    public String json() {
        System.exit(-7);
        return "";
    }

    @POST
    @Path("/systemExit")
    @Consumes({MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN, MediaType.APPLICATION_OCTET_STREAM})
    @Produces(MediaType.APPLICATION_JSON)
    public String json(String data) {
        System.exit(-8);
        return "";
    }

    @GET
    @Path("/stackOverflow")
    @Consumes({MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN, MediaType.APPLICATION_OCTET_STREAM})
    @Produces(MediaType.APPLICATION_JSON)
    public String json2() {
        // intentional stack overflow
        return this.json2();
    }

    @POST
    @Path("/stackOverflow")
    @Consumes({MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN, MediaType.APPLICATION_OCTET_STREAM})
    @Produces(MediaType.APPLICATION_JSON)
    public String json2(String data) {
        // intentional stack overflow
        return this.json2(data);
    }

    @GET
    @Path("/runtimeException")
    @Consumes({MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN, MediaType.APPLICATION_OCTET_STREAM})
    @Produces(MediaType.APPLICATION_JSON)
    public String json3() {
        throw new RuntimeException("Handcrafted runtime exception.");
    }

    @POST
    @Path("/runtimeException")
    @Consumes({MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN, MediaType.APPLICATION_OCTET_STREAM})
    @Produces(MediaType.APPLICATION_JSON)
    public String json3(String data) {
        throw new RuntimeException("Handcrafted runtime exception with data=" + data + ".");
    }
}
