import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.bson.Document;

public class Result {
    public  Result() {}

    public Result(int status, String message) {
        this.message = message;
        this.status = status;
    }

    private int status;
    private String message;

    public int getStatus() {
        return this.status;
    }

    public  String getMessage() {
        return this.message;
    }
    public void setStatus(int status) {
        this.status = status;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    @Override
    public String toString() {
        return "Status: " + this.status + ", " + "Message: " + this.message;
    }
    public String toJSON() {
        GsonBuilder gsonBuilder = new GsonBuilder();
        gsonBuilder.setPrettyPrinting();
        Gson gson = gsonBuilder.create();
        return gson.toJson(this);
    }
}
