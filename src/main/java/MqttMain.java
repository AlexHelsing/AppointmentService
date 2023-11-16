import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.mongodb.client.*;
import org.bson.Document;
import org.bson.codecs.configuration.CodecProvider;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import javax.net.ssl.SSLSocketFactory;
import java.util.ArrayList;
import java.util.List;

import static com.mongodb.MongoClientSettings.getDefaultCodecRegistry;
import static com.mongodb.client.model.Filters.eq;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.bson.codecs.configuration.CodecRegistries.fromProviders;
import static org.bson.codecs.configuration.CodecRegistries.fromRegistries;


public class MqttMain {

    // MongoDB
    static String MongoDbURI = AppConfig.getMongodbUri();

    // MQTT Connection
    static String MqttURI = AppConfig.getMqttUri();
    static String MqttUsername = AppConfig.getMqttUsername();
    static String MqttPassword = AppConfig.getMqttPassword();

    // TOPICS

    static String createAppointment = "/appointment/create";
    static String requestAppointment = "/appointment/request";
    static String testing = "/testing";


    public static void main(String[] args) throws MqttException {

        CodecProvider pojoCodecProvider = PojoCodecProvider.builder().automatic(true).build();
        CodecRegistry pojoCodecRegistry = fromRegistries(getDefaultCodecRegistry(), fromProviders(pojoCodecProvider));

        Gson gson = new Gson();

        MqttClient client = new MqttClient(
                MqttURI, // serverURI in format:
                // "protocol://name:port"
                MqttClient.generateClientId(), // ClientId
                new MemoryPersistence()); // Persistence

        MqttConnectOptions mqttConnectOptions = new MqttConnectOptions();
        mqttConnectOptions.setUserName(MqttUsername);
        mqttConnectOptions.setPassword(MqttPassword.toCharArray());
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
                System.out.println("Received message on " + topic + ": ");
                byte[] payload = message.getPayload();
                String text = new String(payload, UTF_8);

                switch (topic) {
                    case "/appointment/request":
                        try (MongoClient mongoClient = MongoClients.create(MongoDbURI)) {
                            MongoDatabase database = mongoClient.getDatabase("appointments");
                            MongoCollection<Document> collection = database.getCollection("appointment");
                            Document query = new Document("patient", text);
                            FindIterable<Document> matchingDocs = collection.find(query);
                            Document patientExists = collection.find(eq("patient", text)).first();
                            if (patientExists != null) {
                                List<String> docJsonList = new ArrayList<>();
                                for (Document document : matchingDocs) {
                                    String docJson = document.toJson();
                                    docJsonList.add(docJson);
                                }
                                String jsonArray = "[" + String.join(",", docJsonList) + "]";
                                //String appointmentRequest = String.format("/appointment/request/%s", "patient");
                                //String docJson = doc.toJson();
                                System.out.println(jsonArray);
                                byte[] messagePayload = jsonArray.getBytes();
                                MqttMessage publishMessage = new MqttMessage(messagePayload);
                                client.publish("/appointment/request/user", publishMessage);
                            } else {
                                System.out.println("No matching documents found.");
                            }
                        } catch (MqttException e) {
                            throw new RuntimeException(e);
                        }
                        break;
                    case "/appointment/create":
                        try (MongoClient mongoClient = MongoClients.create(MongoDbURI)) {
                            MongoDatabase database = mongoClient.getDatabase("appointments").withCodecRegistry(pojoCodecRegistry);
                            MongoCollection<Appointment> collection = database.getCollection("appointment", Appointment.class);
                            ObjectMapper objectMapper = new ObjectMapper();
                            objectMapper.findAndRegisterModules();
                            Appointment appointment = objectMapper.readValue(text, Appointment.class);
                            collection.insertOne(appointment);
                            System.out.println("Successfully created new appointment. \n" + " Patient:  " + appointment.getPatient()  + " \n Dentist: " + appointment.getDentist() + " \n Date and time: " + appointment.getDateTime());
                            //List<Appointment> appointments = new ArrayList<>();
                            //collection.find().into(appointments);
                            //System.out.println(appointments);
                            break;
                        } catch (JsonProcessingException e) {
                            throw new RuntimeException(e);
                        }
                    case "/testing":
                            System.out.println(text);

                            break;
                        }
            }


            @Override
            // Called when an outgoing publish is complete
            public void deliveryComplete(IMqttDeliveryToken token) {
                System.out.println("delivery complete " + token);
            }
        });

        client.subscribe(createAppointment, 1); // subscribe to everything with QoS = 1
        client.subscribe(requestAppointment, 1); // subscribe to everything with QoS = 1
        client.subscribe(testing, 1); // subscribe to everything with QoS = 1

    }



}
