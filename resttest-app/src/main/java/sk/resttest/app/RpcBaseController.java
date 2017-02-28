package sk.resttest.app;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

/**
 * TODO when finished, put it into nike-rpc-server project as some base abstract class.
 *
 * Created by Ľubomír Varga on 27.2.2017.
 */
@Path("/rpc")
public class RpcBaseController {
    @GET
    @Path("/isAlive")
    @Consumes({MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN, MediaType.APPLICATION_OCTET_STREAM})
    @Produces(MediaType.APPLICATION_JSON)
    public String isAlive() {
        return "{\"isAlive\":true}";
    }
}
