import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.gson.JsonArray;
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
import java.util.Arrays;
import java.util.List;

import static com.mongodb.MongoClientSettings.getDefaultCodecRegistry;
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
    static String dentistAddAppointmentSlot = "Dentist/add_appointment_slots/req";
    static String patientMakeAppointment = "Patient/make_appointment/req";
    static String patientGetAppointments = "Patient/get_appointments/req";
    static String patientGetAllAppointments = "Patient/get_all_appointments/req";
    static String patientCancelAppointment = "Patient/cancel_appointment/req";
    static String clinicGetAppointments = "Clinic/get_appointments/req";

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
                // All messageArrived can be found in the subscriptions below.
            }
            @Override
            // Called when an outgoing publish is complete
            public void deliveryComplete(IMqttDeliveryToken token) {
                System.out.println("delivery complete " + token);
            }
        });

        // Patient Subscriptions
        client.subscribe(patientMakeAppointment, 1, (topic, message) -> {
            byte[] payload = message.getPayload();
            String text = new String(payload, UTF_8);
            System.out.println("Received message on " + topic + " \nMessage: " + text);
            try (MongoClient mongoClient = MongoClients.create(MongoDbURI)) {
                MongoDatabase database = mongoClient.getDatabase("appointments").withCodecRegistry(pojoCodecRegistry);
                MongoCollection<Appointment> collection = database.getCollection("appointment", Appointment.class);
                Document jsonDocument = Document.parse(text);
                System.out.println(jsonDocument);
                String newPatientId = jsonDocument.getString("patientId");
                String responseTopic = jsonDocument.getString("responseTopic");
                System.out.println(responseTopic);
                // Find matching appointmentId in db, append patientID + change boolean isBooked to "true"
                Document query = new Document("_id", new ObjectId(jsonDocument.getString("_id")));
                Document update = new Document("$set", new Document("patientId", newPatientId)
                        .append("isBooked", "true"));
                FindOneAndUpdateOptions options = new FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER);
                Appointment updatedDocument = collection.findOneAndUpdate(query, update, options);
                if (updatedDocument != null){
                    String mqttResponseTopic = String.format("Patient/%s/make_appointment/res", responseTopic);
                    String updatedJson = updatedDocument.toJson();
                    byte[] bookedMessage = updatedJson.getBytes();
                    MqttMessage publishMessage = new MqttMessage(bookedMessage);
                    client.publish(mqttResponseTopic, publishMessage);
                }
            } catch (MqttException e) {
                throw new RuntimeException(e);
            }
        });
        client.subscribe(patientGetAppointments, 1, (topic, message) -> {
            byte[] payload = message.getPayload();
            String text = new String(payload, UTF_8);
            System.out.println("Received message on " + topic + " \nMessage: " + text);
            try (MongoClient mongoClient = MongoClients.create(MongoDbURI)) {
                MongoDatabase database = mongoClient.getDatabase("appointments");
                MongoCollection<Document> collection = database.getCollection("appointment");
                // Parse and query database with the patientId string in payload text
                Document jsonDocument = Document.parse(text);
                String queryPatientId = jsonDocument.getString("patientId");
                String responseTopic = jsonDocument.getString("responseTopic");
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
                String mqttResponseTopic = String.format("Patient/%s/get_appointments/res", responseTopic);
                byte[] messagePayload = jsonArray.getBytes();
                MqttMessage publishMessage = new MqttMessage(messagePayload);
                client.publish(mqttResponseTopic, publishMessage);
            } catch (MqttException e) {
                throw new RuntimeException(e);
            }
        });
        client.subscribe(patientCancelAppointment, 1, (topic, message) -> {
            byte[] payload = message.getPayload();
            String text = new String(payload, UTF_8);
            System.out.println("Received message on " + topic + " \nMessage: " + text);
            try (MongoClient mongoClient = MongoClients.create(MongoDbURI)) {
                MongoDatabase database = mongoClient.getDatabase("appointments").withCodecRegistry(pojoCodecRegistry);
                MongoCollection<Document> collection = database.getCollection("appointment");
                Document jsonDocument = Document.parse(text);
                String queryAppointmentId = jsonDocument.getString("_id");
                String responseTopic = jsonDocument.getString("responseTopic");
                ObjectId queryId = new ObjectId(queryAppointmentId);
                Document query = new Document("_id", queryId);
                // TODO: Check if better solution for querying then removing. Probably there is
                if(collection.find(query).first() != null){
                    if (collection.deleteOne(query).wasAcknowledged()){
                        String cancelMessagePayload = "Deleted appointment successfully";
                        byte[] cancelMessage = cancelMessagePayload.getBytes();
                        MqttMessage publishMessage = new MqttMessage(cancelMessage);
                        String mqttResponseTopic = String.format("Patient/%s/cancel_appointment/res", responseTopic);
                        client.publish(mqttResponseTopic, publishMessage);
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
        });
        client.subscribe(patientGetAllAppointments, 1, (topic, message) -> {
            byte[] payload = message.getPayload();
            String text = new String(payload, UTF_8);
            System.out.println("Received message on " + topic + " \nMessage: " + text);
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
        });
        // Dentist subscriptions
        client.subscribe(dentistAddAppointmentSlot, 1, (topic, message) -> {
            byte[] payload = message.getPayload();
            String text = new String(payload, UTF_8);
            System.out.println("Received message on " + topic + " \nMessage: " + text);
            try (MongoClient mongoClient = MongoClients.create(MongoDbURI)) {
                MongoDatabase database = mongoClient.getDatabase("appointments").withCodecRegistry(pojoCodecRegistry);
                MongoCollection<Appointment> collection = database.getCollection("appointment", Appointment.class);
                // Read from JSON and create a POJO object to insert to database
                ObjectMapper objectMapper = new ObjectMapper();
                JsonNode jsonNode = objectMapper.readTree(text);
                String responseTopic = jsonNode.get("responseTopic").asText();
                ((ObjectNode) jsonNode).remove("responseTopic");
                String jsonWithoutResponseTopic = jsonNode.toString();
                objectMapper.findAndRegisterModules();
                Appointment appointment = objectMapper.readValue(jsonWithoutResponseTopic, Appointment.class);
                InsertOneResult newAppointment = collection.insertOne(appointment);
                // If insertion is acknowledged, publish to response topic
                if(newAppointment.wasAcknowledged()){
                    String mqttResponseTopic = String.format("Dentist/%s/add_appointment_slot/res", responseTopic);
                    String addedSlot = appointment.toJson();
                    byte[] addedSlotByte = addedSlot.getBytes();
                    MqttMessage addedSlotMsg = new MqttMessage(addedSlotByte);
                    client.publish(mqttResponseTopic, addedSlotMsg);

                }
            } catch (JsonProcessingException | MqttException e) {
                throw new RuntimeException(e);
            }
        });
        // Clinic subscriptions
        client.subscribe(clinicGetAppointments, 1, (topic, message) -> {
            long start = System.nanoTime();
            byte[] payload = message.getPayload();
            String text = new String(payload, UTF_8);
            System.out.println("Received message on " + topic + " \nMessage: " + text);
            try (MongoClient mongoClient = MongoClients.create(MongoDbURI)) {
                MongoDatabase database = mongoClient.getDatabase("appointments");
                MongoCollection<Document> collection = database.getCollection("appointment");
                // Parse and query database with the patientId string in payload text
                ObjectMapper objectMapper = new ObjectMapper();
                JsonNode jsonNode = objectMapper.readTree(text);
                String responseTopic = jsonNode.get("responseTopic").asText();
                ((ObjectNode) jsonNode).remove("responseTopic");
                JsonNode dentistsNode = jsonNode.get("dentists");
                List<String> dentistIds = objectMapper.convertValue(dentistsNode, new TypeReference<List<String>>() {});
                Document filter = new Document("dentistId", new Document("$in", dentistIds));
                List<Document> matchingAppointments = collection.find(filter).into(new ArrayList<>());
                ArrayList<String> docJsonList = new ArrayList<>();
                for (Document appointment : matchingAppointments) {
                    String docJson = appointment.toJson();
                    docJsonList.add(docJson);
                }
                // Create Json format, format to MQTT message and publish to response topic
                String jsonArray = "[" + String.join(",", docJsonList) + "]";
                System.out.println(jsonArray);
                String mqttResponseTopic = String.format("Clinic/%s/get_appointments/res", responseTopic);
                byte[] messagePayload = jsonArray.getBytes();
                MqttMessage publishMessage = new MqttMessage(messagePayload);
                client.publish(mqttResponseTopic, publishMessage);
                long finish = System.nanoTime();
                long timeElapse = finish - start;
                System.out.println(timeElapse/1000000);
            } catch (MqttException | JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        });



    }
}
