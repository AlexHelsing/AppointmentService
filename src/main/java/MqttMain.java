import com.fasterxml.jackson.core.JsonProcessingException;
import com.mongodb.client.*;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.result.InsertManyResult;
import org.bson.Document;
import org.bson.codecs.configuration.CodecProvider;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import javax.net.ssl.SSLSocketFactory;

import java.time.LocalDate;
import java.util.ArrayList;
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
    static String dentistAddAppointmentSlot = "Clinic/post_slots/req";
    static String patientMakeAppointment = "Patient/make_appointment/req";
    static String patientGetAppointments = "Patient/get_appointments/req";
    static String patientGetAllAppointments = "Patient/get_all_appointments/req";
    static String patientCancelAppointment = "Patient/cancel_appointment/req";
    static String clinicGetAppointments = "Clinic/get_appointments/req";

    static String clinicGetAppointmentsDate = "Clinic/get_appointments/date/req";

    // Codec
    static CodecProvider pojoCodecProvider = PojoCodecProvider.builder().automatic(true).build();
    static CodecRegistry pojoCodecRegistry = fromRegistries(getDefaultCodecRegistry(), fromProviders(pojoCodecProvider));

    public static void main(String[] args) throws MqttException {
        // setting the Mqtt connection
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

        //Setting the DB connection
        MongoClient mongoClient = MongoClients.create(AppConfig.getMongodbUri());
        MongoCollection<Appointment> collection = Utilities.getCollection(mongoClient);

        // Patient Subscriptions
        client.subscribe(patientMakeAppointment, 1, (topic, message) -> {
            byte[] payload = message.getPayload();
            String text = new String(payload, UTF_8);
            System.out.println("Received message on " + topic + " \nMessage: " + text);
            try  {
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
                    String updatedJson = updatedDocument.toJSON();
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
            try  {
                // Parse and query database with the patientId string in payload text
                Document jsonDocument = Document.parse(text);
                String queryPatientId = jsonDocument.getString("patientId");
                String responseTopic = jsonDocument.getString("responseTopic");
                Document query = new Document("patientId", queryPatientId);
                FindIterable<Appointment> matchingDocs = collection.find(query);
                // Find and add all the matches of the query to docJsonList
                List<String> docJsonList = new ArrayList<>();
                for (Appointment doc : matchingDocs) {
                    String docJson = doc.toJSON();
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
            try  {
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
            try {
                // Find all documents in collection and add to JSON list
                FindIterable<Appointment> matchingDocs = collection.find();
                List<String> docJsonList = new ArrayList<>();
                for (Appointment document : matchingDocs) {
                    String docJson = document.toJSON();
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
            String payload = Utilities.payloadToString(message.getPayload());
            System.out.println("Received message on " + topic + " \nMessage: " + payload);
            try {
                // Read from JSON and create a POJO object to insert to database
                ArrayList<Appointment> appointments = Utilities.convertToAppointments(payload);
                String responseTopic = Utilities.extractResponseTopic(payload);

                System.out.println("Appointments: " + appointments);
                System.out.println("responseTopic" + responseTopic);

                InsertManyResult newAppointments = collection.insertMany(appointments);
                // If insertion is acknowledged, publish to response topic
                if(newAppointments.wasAcknowledged()){
                    String mqttResponseTopic = String.format("Clinic/%s/post_slots/res", responseTopic);

                    Result result = new Result(201, "Appointment slots were added successfully.");
                    String resPayload = result.toJSON();
                    byte[] resPayloadBytes = resPayload.getBytes();
                    MqttMessage response = new MqttMessage(resPayloadBytes);

                    client.publish(mqttResponseTopic, response);
                }
            } catch (JsonProcessingException | MqttException e) {
                throw new RuntimeException(e);
            }
        });
        // Clinic subscriptions
        client.subscribe(clinicGetAppointments, 1, (topic, message) -> {
            String payload = Utilities.payloadToString(message.getPayload());
            System.out.println("Received message on " + topic + " \nMessage: " + payload);
            try {
                ArrayList<ObjectId> dentistIds = Utilities.convertToDentistIds(payload);
                String responseTopic = Utilities.extractResponseTopic(payload);

                // Query Appointments based on dentistIds
                Bson filter = Filters.in("dentistId", dentistIds);
                ArrayList<Appointment> matchingAppointments = new ArrayList<Appointment>();
                collection.find(filter).into(matchingAppointments);
                //System.out.println("Matching appointments: " +  matchingAppointments);

                // Structure payload as an array of JSONs
                ArrayList<String> jsonAppointments = new ArrayList<>();
                for (Appointment appointment :  matchingAppointments) {

                    String jsonAppointment = appointment.toJSON();
                    jsonAppointments.add(jsonAppointment);
                }

                Result result = new Result(200, "Appointments were retrieved successfully.");
                String resultJson = result.toJSON();

                String resPayload = "";
                if(jsonAppointments.isEmpty()) {
                    resPayload = "[" + resultJson + "]";
                }
                else {
                    resPayload = "[" + String.join(", ", jsonAppointments) + "," + resultJson + "]";
                }
                System.out.println(resPayload);

                String mqttResponseTopic = String.format("Clinic/%s/get_appointments/res", responseTopic);
                byte[] messagePayload = resPayload.getBytes();
                MqttMessage publishMessage = new MqttMessage(messagePayload);

                client.publish(mqttResponseTopic, publishMessage);
            } catch (MqttException | JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        });
        client.subscribe(clinicGetAppointmentsDate, 1, (topic, message) -> {
            String payload = Utilities.payloadToString(message.getPayload());
            System.out.println("Received message on " + topic + " \nMessage: " + payload);
            try {
                ArrayList<ObjectId> dentistIds = Utilities.convertToDentistIds(payload);
                String responseTopic = Utilities.extractResponseTopic(payload);
                LocalDate date = Utilities.extractDate(payload);


                // Query Appointments based on dentistIds
                Bson filter = Filters.in("dentistId", dentistIds);
                Bson dateFilter = Filters.eq("date", date);
                Bson combinedFilter = Filters.and(filter, dateFilter);
                ArrayList<Appointment> matchingAppointments = new ArrayList<>();
                collection.find(combinedFilter).into(matchingAppointments);
                //System.out.println("Matching appointments: " +  matchingAppointments);

                // Structure payload as an array of JSONs
                ArrayList<String> jsonAppointments = new ArrayList<>();
                for (Appointment appointment :  matchingAppointments) {

                    String jsonAppointment = appointment.toJSON();
                    jsonAppointments.add(jsonAppointment);
                }

                Result result = new Result(200, "Appointments were retrieved successfully.");
                String resultJson = result.toJSON();

                String resPayload = "";
                if(jsonAppointments.isEmpty()) {
                    resPayload = "[" + resultJson + "]";
                }
                else {
                    resPayload = "[" + String.join(", ", jsonAppointments) + "," + resultJson + "]";
                }
                System.out.println(resPayload);

                String mqttResponseTopic = String.format("Clinic/%s/get_appointments/res", responseTopic);
                byte[] messagePayload = resPayload.getBytes();
                MqttMessage publishMessage = new MqttMessage(messagePayload);

                client.publish(mqttResponseTopic, publishMessage);
            } catch (MqttException | JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        });
    }
}
