import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.MongoException;
import com.mongodb.client.*;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.result.InsertOneResult;
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

    // Codec
    static CodecProvider pojoCodecProvider = PojoCodecProvider.builder().automatic(true).build();
    static CodecRegistry pojoCodecRegistry = fromRegistries(getDefaultCodecRegistry(), fromProviders(pojoCodecProvider));


    public static void main(String[] args) throws MqttException {

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
                byte[] payload = message.getPayload();
                String text = new String(payload, UTF_8);
                System.out.println("Received message on " + topic + " \nMessage: " + text);

                switch (topic) {
                    case "Patient/get_all_appointments/req":
                        // For now, this returns EVERY appointment in the whole database collection.
                        try (MongoClient mongoClient = MongoClients.create(MongoDbURI)) {
                            MongoDatabase database = mongoClient.getDatabase("appointments").withCodecRegistry(pojoCodecRegistry);
                            MongoCollection<Document> collection = database.getCollection("appointment");
                            // Find all documents in collection and add to JSON list
                            FindIterable<Document> matchingDocs = collection.find();
                            List<String> docJsonList = new ArrayList<>();
                            for (Document document : matchingDocs) {
                                String docJson = document.toJson();
                                docJsonList.add(docJson);
                            }
                            // Create JSON array from docJsonList
                            String jsonArray = "[" + String.join(",", docJsonList) + "]";
                            System.out.println(jsonArray);
                            // Create MQTT message of String and publish
                            byte[] messagePayload = jsonArray.getBytes();
                            MqttMessage publishMessage = new MqttMessage(messagePayload);
                            client.publish("Patient/get_all_appointments/res", publishMessage);
                        } catch (MqttException e) {
                            throw new RuntimeException(e);
                        }
                        break;

                    case "Dentist/add_appointment_slots/req":
                        try (MongoClient mongoClient = MongoClients.create(MongoDbURI)) {
                            MongoDatabase database = mongoClient.getDatabase("appointments").withCodecRegistry(pojoCodecRegistry);
                            MongoCollection<Appointment> collection = database.getCollection("appointment", Appointment.class);
                            // Read from JSON and create a POJO object to insert to database
                            ObjectMapper objectMapper = new ObjectMapper();
                            objectMapper.findAndRegisterModules();
                            Appointment appointment = objectMapper.readValue(text, Appointment.class);
                            InsertOneResult newAppointment = collection.insertOne(appointment);
                            // If insertion is acknowledged, publish to response topic
                            if(newAppointment.wasAcknowledged()){
                                String addedSlot = appointment.toJson();
                                byte[] addedSlotByte = addedSlot.getBytes();
                                MqttMessage addedSlotMsg = new MqttMessage(addedSlotByte);
                                client.publish("/Dentist/add_appointment_slot/res", addedSlotMsg);

                            }
                        } catch (JsonProcessingException | MqttException e) {
                            throw new RuntimeException(e);
                        }
                        break;

                    case "Patient/get_appointments/req":
                        try (MongoClient mongoClient = MongoClients.create(MongoDbURI)) {
                            MongoDatabase database = mongoClient.getDatabase("appointments");
                            MongoCollection<Document> collection = database.getCollection("appointment");
                            // Parse and query database with the patientId string in payload text
                            Document jsonDocument = Document.parse(text);
                            String queryPatientId = jsonDocument.getString("patientId");
                            Document query = new Document("patientId", queryPatientId);
                            FindIterable<Document> matchingDocs = collection.find(query);
                            // Find and add all the matches of the query to docJsonList
                            List<String> docJsonList = new ArrayList<>();
                            for (Document doc : matchingDocs) {
                                String docJson = doc.toJson();
                                docJsonList.add(docJson);
                            }
                            // Create Json format, format to MQTT message and publish to response topic
                            String jsonArray = "[" + String.join(",", docJsonList) + "]";
                            System.out.println(jsonArray);

                            byte[] messagePayload = jsonArray.getBytes();
                            MqttMessage publishMessage = new MqttMessage(messagePayload);
                            client.publish("Patient/get_appointments/res", publishMessage);
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
                            // Find matching appointmentId in db, append patientID + change boolean isBooked to "true"
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
                                }
                        } catch (MqttException e) {
                            throw new RuntimeException(e);
                        }
                        break;

                    case "Patient/cancel_appointment/req":
                        System.out.println(text);
                        try (MongoClient mongoClient = MongoClients.create(MongoDbURI)) {
                            MongoDatabase database = mongoClient.getDatabase("appointments").withCodecRegistry(pojoCodecRegistry);
                            MongoCollection<Document> collection = database.getCollection("appointment");
                            Document jsonDocument = Document.parse(text);
                            String queryAppointmentId = jsonDocument.getString("_id");
                            ObjectId queryId = new ObjectId(queryAppointmentId);
                            Document query = new Document("_id", queryId);
                            // TODO: Check if better solution for querying then removing. Probably there is
                            if(collection.find(query).first() != null){
                                if (collection.deleteOne(query).wasAcknowledged()){
                                    String cancelMessagePayload = "Deleted appointment successfully";
                                    byte[] cancelMessage = cancelMessagePayload.getBytes();
                                    MqttMessage publishMessage = new MqttMessage(cancelMessage);
                                    client.publish("/Patient/cancel_appointment/res", publishMessage);
                                    }
                                else{
                                    System.out.println("Failed to delete data");
                                    String cancelMessagePayload = "Failed to delete";
                                    byte[] cancelMessage = cancelMessagePayload.getBytes();
                                    MqttMessage publishMessage = new MqttMessage(cancelMessage);
                                    client.publish("/Patient/cancel_appointment/res", publishMessage);
                                }
                            }
                        else{
                                System.out.println("No documents found");
                                String cancelMessagePayload = "No document found";
                                byte[] cancelMessage = cancelMessagePayload.getBytes();
                                MqttMessage publishMessage = new MqttMessage(cancelMessage);
                                client.publish("/Patient/cancel_appointment/res", publishMessage);
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
        client.subscribe("Patient/get_all_appointments/req", 1);

    }
}
