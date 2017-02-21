package sk.httpclient.client;

import com.netflix.ribbon.ResponseValidator;
import com.netflix.ribbon.ServerError;
import com.netflix.ribbon.UnsuccessfulResponseException;
import io.netty.buffer.ByteBuf;
import io.reactivex.netty.protocol.http.client.HttpClientResponse;

public class Validator implements ResponseValidator<HttpClientResponse<ByteBuf>> {
    private String procedureName;

    public Validator(String procedureName) {
        this.procedureName = procedureName;
    }

    @Override
    public void validate(HttpClientResponse<ByteBuf> response) throws UnsuccessfulResponseException, ServerError {
        if (response.getStatus().code() > 399 && response.getStatus().code() < 500 || response.getStatus().code() < 200) {
            throw new UnsuccessfulResponseException(getMessage(procedureName, response));
        }

        if (response.getStatus().code() > 499) {
            throw new ServerError(getMessage(procedureName, response));
        }
    }

    private String getMessage(String procedureName, HttpClientResponse<ByteBuf> response) {
        return "Server error with procedure name: " + procedureName + " status: " + response.getStatus().code() + " reason: " + response.getStatus().reasonPhrase();
    }


}
