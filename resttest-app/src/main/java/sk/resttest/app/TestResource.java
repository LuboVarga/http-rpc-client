package sk.resttest.app;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

@Path("/test")
public class TestResource {


    @GET
    @Path("/test")
    @Produces(MediaType.APPLICATION_JSON)
    public String test() {
        return "{ \"name\":\"John\", \"age\":31, \"city\":\"New York\" }";
    }

    @POST
    @Path("/")
    @Produces(MediaType.APPLICATION_JSON)
    public String test2(String data) {
        System.out.println("Received data: " + data);

        return "{ \"name\":\"John\", \"age\":31, \"city\":\"New York\" }";
    }
}
