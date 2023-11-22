public class AppConfigExample {

    // Rename to AppConfig.java and enter usernames and password to below fields
    private static final String MONGODB_URI ="mongodb+srv://<username>:<password>@cluster0.qlrcbzs.mongodb.net/?retryWrites=true&w=majority";
    private static final String MQTT_URI = "<mqttUri>";

    private static final String MQTT_USERNAME = "<username>";

    private static final String MQTT_PASSWORD = "<password>";


    public static String getMongodbUri(){
        return MONGODB_URI;
    }
    public static String getMqttUri(){
        return MQTT_URI;
    }
    public static String getMqttUsername(){
        return MQTT_USERNAME;
    }
    public static String getMqttPassword(){
        return MQTT_PASSWORD;
    }

}
