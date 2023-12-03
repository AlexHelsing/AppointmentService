import com.fasterxml.jackson.annotation.JsonProperty;
import org.bson.Document;

public class Result extends Document {
    public  Result() {}

    public Result(int status, String message) {
        this.message = message;
        this.status = status;
    }
    @JsonProperty("status")
    private int status;

    @JsonProperty("message")
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
}
