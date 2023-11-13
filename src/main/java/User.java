import org.bson.types.ObjectId;

public class User {

    public User() {}

    private ObjectId id;
    private String username;
    private String password;

    public User(String username, String password){
        this.username = username;
        this.password = password;
    }

    public ObjectId getId(){
        return id;
    }
    public void setId(ObjectId id){
        this.id = id;
    }

    public String getUserName() {
        return username;
    }

    public void setUserName(String userName) {
        this.username = userName;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String toString(){
        return  username;
    }
}
