import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.util.JSONPObject;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.mongodb.client.*;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.ReturnDocument;
import org.bson.Document;
import org.bson.codecs.configuration.CodecProvider;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;
import org.bson.types.ObjectId;
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

    static String dentistCreateSlotAppointment = "Dentist/add_appointment_slots/req";
    static String patientCreateAppointment = "Patient/make_appointment/req";

    static String patientRequestAppointment = "Patient/get_appointments/req";
    static String patientCancelAppointment = "Patient/cancel_appointment/req";


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
                    case "Dentist/add_appointment_slots/req":
                        try (MongoClient mongoClient = MongoClients.create(MongoDbURI)) {
                            MongoDatabase database = mongoClient.getDatabase("appointments").withCodecRegistry(pojoCodecRegistry);
                            MongoCollection<Appointment> collection = database.getCollection("appointment", Appointment.class);
                            ObjectMapper objectMapper = new ObjectMapper();
                            objectMapper.findAndRegisterModules();
                            Appointment appointment = objectMapper.readValue(text, Appointment.class);
                            collection.insertOne(appointment);
                            String addedSlot = "Added slot successfully: ";
                            byte[] addedSlotByte = addedSlot.getBytes();
                            MqttMessage addedSlotMsg = new MqttMessage(addedSlotByte);
                            client.publish("/Dentist/add_appointment_slot/res", addedSlotMsg);

                        } catch (JsonProcessingException | MqttException e) {
                            throw new RuntimeException(e);
                        }
                        break;
                    case "Patient/get_appointments/req":
                        try (MongoClient mongoClient = MongoClients.create(MongoDbURI)) {
                            MongoDatabase database = mongoClient.getDatabase("appointments");
                            MongoCollection<Document> collection = database.getCollection("appointment");
                            Document query = new Document("patientId", text);
                            FindIterable<Document> matchingDocs = collection.find(query);
                            // Find better solution for this check. It queries only to check if ptient exists.
                            Document patientExists = collection.find(eq("patientId", text)).first();
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
                    case "Patient/make_appointment/req":
                        try (MongoClient mongoClient = MongoClients.create(MongoDbURI)) {
                            MongoDatabase database = mongoClient.getDatabase("appointments").withCodecRegistry(pojoCodecRegistry);
                            MongoCollection<Appointment> collection = database.getCollection("appointment", Appointment.class);
                            Document jsonDocument = Document.parse(text);
                            String newPatientId = jsonDocument.getString("patientId");
                            Document query = new Document("_id", new ObjectId(jsonDocument.getString("_id")));
                            Document update = new Document("$set", new Document("patientId", newPatientId)
                                    .append("isBooked", "true"));
                            FindOneAndUpdateOptions options = new FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER);
                            Appointment updatedDocument = collection.findOneAndUpdate(query, update, options);
                            if (updatedDocument != null){
                            String updatedJson = updatedDocument.toJson();
                            byte[] bookedMessage = updatedJson.getBytes();
                            MqttMessage publishMessage = new MqttMessage(bookedMessage);
                            client.publish("Patient/make_appointment/res", publishMessage);
                        }} catch (MqttException e) {
                            throw new RuntimeException(e);
                        }
                        break;
                    case "Patient/cancel_appointment/req":
                        System.out.println(text);
                        try (MongoClient mongoClient = MongoClients.create(MongoDbURI)) {
                            MongoDatabase database = mongoClient.getDatabase("appointments").withCodecRegistry(pojoCodecRegistry);
                            MongoCollection<Document> collection = database.getCollection("appointment");
                            ObjectId objectIdToQuery = new ObjectId(text);
                            Document query = new Document("_id", objectIdToQuery);
                            Document cancelAppointment = collection.find(query).first();
                            if (cancelAppointment != null){
                                collection.deleteOne(query);
                                System.out.println("Deleted appointment id " + text);
                                String cancelMessagePayload = "Deleted appointment successfully";
                                byte[] cancelMessage = cancelMessagePayload.getBytes();
                                MqttMessage publishMessage = new MqttMessage(cancelMessage);
                                client.publish("/patient/cancel_appointment/response", publishMessage);
                            }
                            else{
                                System.out.println("No document found");
                                }
                            } catch (MqttException e) {
                            throw new RuntimeException(e);
                        }
                        break;
            }}


            @Override
            // Called when an outgoing publish is complete
            public void deliveryComplete(IMqttDeliveryToken token) {
                System.out.println("delivery complete " + token);
            }
        });

        client.subscribe(patientCreateAppointment, 1); // subscribe to everything with QoS = 1
        client.subscribe(patientRequestAppointment, 1); // subscribe to everything with QoS = 1
        client.subscribe(patientCancelAppointment, 1); // subscribe to everything with QoS = 1
        client.subscribe(dentistCreateSlotAppointment, 1);

    }
}
