import org.bson.codecs.configuration.CodecProvider;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import javax.net.ssl.SSLSocketFactory;

import static com.mongodb.MongoClientSettings.getDefaultCodecRegistry;
import static java.nio.charset.StandardCharsets.UTF_8;

import static com.mongodb.client.model.Filters.eq;
import static org.bson.codecs.configuration.CodecRegistries.fromProviders;
import static org.bson.codecs.configuration.CodecRegistries.fromRegistries;

import org.bson.Document;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;

import java.util.ArrayList;
import java.util.List;


public class MqttMain {


    public static void main(String[] args) throws MqttException {

        CodecProvider pojoCodecProvider = PojoCodecProvider.builder().automatic(true).build();
        CodecRegistry pojoCodecRegistry = fromRegistries(getDefaultCodecRegistry(), fromProviders(pojoCodecProvider));

        String uri = AppConfig.getMongodbUri();


        MqttClient client = new MqttClient(
                AppConfig.getMqttUri(), // serverURI in format:
                // "protocol://name:port"
                MqttClient.generateClientId(), // ClientId
                new MemoryPersistence()); // Persistence

        MqttConnectOptions mqttConnectOptions = new MqttConnectOptions();
        mqttConnectOptions.setUserName(AppConfig.getMqttUsername());
        mqttConnectOptions.setPassword(AppConfig.getMqttPassword().toCharArray());
        // using the default socket factory
        mqttConnectOptions.setSocketFactory(SSLSocketFactory.getDefault());
        client.connect(mqttConnectOptions);


        client.setCallback(new MqttCallback() {
            @Override
            // Called when the client lost the connection to the broker
            public void connectionLost(Throwable cause) {
                System.out.println("client lost connection " + cause);
            }

            @Override
            public void messageArrived(String topic, MqttMessage message) {
                System.out.println(topic + ": ");
                if(topic.equals("/user/request")){
                    byte[] payload = message.getPayload();
                    String text = new String(payload, UTF_8);
                    try (MongoClient mongoClient = MongoClients.create(uri)) {
                        MongoDatabase database = mongoClient.getDatabase("users");
                        MongoCollection<Document> collection = database.getCollection("users");
                        Document doc = collection.find(eq("userName", text)).first();
                        if (doc != null) {
                            System.out.println(doc.toJson());
                        } else {
                            System.out.println("No matching documents found.");
                        }
                    }
                }
                else if(topic.equals("/user/create")){
                    byte[] payload = message.getPayload();
                    String text = new String(payload, UTF_8);
                    String [] usernamePassword = text.split(" ");
                try (MongoClient mongoClient = MongoClients.create(uri)) {
                    MongoDatabase database = mongoClient.getDatabase("users").withCodecRegistry(pojoCodecRegistry);
                    MongoCollection<User> collection = database.getCollection("users", User.class);
                    User user = new User(usernamePassword[0], usernamePassword[1]);
                    collection.insertOne(user);
                    System.out.println("Created new user: " + user.getUserName() + " Password: " + user.getPassword());
                    List<User> users = new ArrayList<>();
                    collection.find().into(users);
                    System.out.println(users);
                        }
                    }}

            @Override
            // Called when an outgoing publish is complete
            public void deliveryComplete(IMqttDeliveryToken token) {
                System.out.println("delivery complete " + token);
            }
        });

        client.subscribe("/user/create", 1); // subscribe to everything with QoS = 1
        client.subscribe("/user/request", 1); // subscribe to everything with QoS = 1

    }

}
