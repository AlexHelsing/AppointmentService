import com.fasterxml.jackson.core.JsonProcessingException;
import com.mongodb.client.*;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.InsertManyResult;
import org.bson.Document;
import org.bson.codecs.configuration.CodecProvider;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


import javax.net.ssl.SSLSocketFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadPoolExecutor;

import static com.mongodb.MongoClientSettings.getDefaultCodecRegistry;
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
    static String dentistAddAppointmentSlot = "Dentist/post_slots/req";
    static String deleteDentistAppointments = "Clinic/delete_dentist/req";
    static String clinicGetAppointments = "Clinic/get_appointments/req";
    static String patientMakeAppointment = "Patient/make_appointment/req";
    static String patientGetAppointments = "Patient/get_appointments/req";
    static String patientCancelAppointment = "Patient/cancel_appointment/req";

    static String dentistGetAppointmentSlots = "Dentist/get_appointments/req";
    static String dentistCancelAppointment = "Dentist/cancel_appointment/req";

    // Codec
    static CodecProvider pojoCodecProvider = PojoCodecProvider.builder().automatic(true).build();
    static CodecRegistry pojoCodecRegistry = fromRegistries(getDefaultCodecRegistry(), fromProviders(pojoCodecProvider));

    public static void main(String[] args) throws MqttException {

        ExecutorService service = Executors.newFixedThreadPool(10);
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
        client.subscribe(patientMakeAppointment, 1, (topic, message) -> service.submit(() -> {
            String payload = Utilities.payloadToString(message.getPayload());
            System.out.println("Received message on " + topic + " \nMessage: " + payload);
            try  {
                String patient_id = Utilities.extractPatientId(payload);
                String responseTopic = Utilities.extractResponseTopic(payload);
                String appointment_id = Utilities.extractAppointmentId(payload);

                String mqttResponseTopic = String.format("Patient/%s/make_appointment/res", responseTopic);

                // Find matching appointmentId in db, append patientID + change boolean isBooked to "true"
                Bson filter = Filters.eq("_id", new ObjectId(appointment_id));
                Appointment appointment =  collection.find(filter).first();

                if (appointment == null) {
                    byte[] messagePayload = new Result(404, "Appointment with given id was not found.").toJSON().getBytes();
                    MqttMessage publishMessage = new MqttMessage(messagePayload);
                    client.publish(mqttResponseTopic, publishMessage);
                    return;
                }

                if (appointment.isBooked()) {
                    byte[] messagePayload = new Result(409, "Appointment is already booked.").toJSON().getBytes();
                    MqttMessage publishMessage = new MqttMessage(messagePayload);
                    client.publish(mqttResponseTopic, publishMessage);
                    return;
                }

                // Booking the appointment
                Document query = new Document("_id", new ObjectId(appointment_id));
                Document update = new Document("$set", new Document("patientId", new ObjectId(patient_id))
                        .append("booked", true));
                FindOneAndUpdateOptions options = new FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER);
                Appointment updatedDocument = collection.findOneAndUpdate(query, update, options);

                if (updatedDocument != null){
                    String result = new Result(200, "Appointment was booked").toJSON();
                    byte[] bookedMessage = result.getBytes();
                    MqttMessage publishMessage = new MqttMessage(bookedMessage);
                    client.publish(mqttResponseTopic, publishMessage);
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }));
        client.subscribe(patientGetAppointments, 1, (topic, message) -> service.submit(() -> {
            String payload = Utilities.payloadToString(message.getPayload());
            System.out.println("Received message on " + topic + " \nMessage: " + payload);
            try  {
                // Parse and query database with the patientId string in payload text
                String patient_id = Utilities.extractPatientId(payload);
                String response_topic = Utilities.extractResponseTopic(payload);

                Document query = new Document("patientId", new ObjectId(patient_id));
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
                String mqttResponseTopic = String.format("Patient/%s/get_appointments/res", response_topic);
                byte[] messagePayload = jsonArray.getBytes();
                MqttMessage publishMessage = new MqttMessage(messagePayload);
                client.publish(mqttResponseTopic, publishMessage);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }));
        client.subscribe(patientCancelAppointment, 1, (topic, message) -> service.submit(() -> {
            String payload = Utilities.payloadToString(message.getPayload());
            System.out.println("Received message on " + topic + " \nMessage: " + payload);
            try  {

                String appointment_id = Utilities.extractAppointmentId(payload);
                String response_topic = Utilities.extractResponseTopic(payload);

                Bson filter = Filters.eq("_id", new ObjectId(appointment_id));
                Appointment appointment = collection.find(filter).first();

                if(appointment == null){
                    String result = new Result(404, "Appointment with given id was not found.").toJSON();
                    MqttMessage publishMessage = new MqttMessage(result.getBytes());
                    String mqttResponseTopic = String.format("Patient/%s/cancel_appointment/res", response_topic);
                    client.publish(mqttResponseTopic, publishMessage);
                    return;
                }
                if (!appointment.isBooked()) {
                    String result = new Result(403, "Appointment with given id is not booked.").toJSON();
                    MqttMessage publishMessage = new MqttMessage(result.getBytes());
                    String mqttResponseTopic = String.format("Patient/%s/cancel_appointment/res", response_topic);
                    client.publish(mqttResponseTopic, publishMessage);
                    return;
                }
                Document query = new Document("_id", new ObjectId(appointment_id));
                Document update = new Document("$set", new Document("patientId", null)
                        .append("booked", false));
                FindOneAndUpdateOptions options = new FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER);
                Appointment updatedDocument = collection.findOneAndUpdate(query, update, options);

                if (updatedDocument != null) {
                    String result = new Result(200, "Appointment was cancelled").toJSON();
                    MqttMessage publishMessage = new MqttMessage(result.getBytes());
                    String mqttResponseTopic = String.format("Patient/%s/cancel_appointment/res", response_topic);
                    client.publish(mqttResponseTopic, publishMessage);
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }));
        // Dentist subscriptions
        client.subscribe(dentistAddAppointmentSlot, 1, (topic, message) -> service.submit(() -> {
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
                    String mqttResponseTopic = String.format("Dentist/%s/post_slots/res", responseTopic);

                    Result result = new Result(201, "Appointment slots were added successfully.");
                    String resPayload = result.toJSON();
                    byte[] resPayloadBytes = resPayload.getBytes();
                    MqttMessage response = new MqttMessage(resPayloadBytes);

                    client.publish(mqttResponseTopic, response);
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }));
        // Dentist appointment slots
        client.subscribe(dentistGetAppointmentSlots, 1, (topic, message) -> service.submit(() -> {
            String payload = Utilities.payloadToString(message.getPayload());
            System.out.println("Received message on " + topic + " \nMessage: " + payload);
            try {
                String responseTopic = Utilities.extractResponseTopic(payload);
                ObjectId dentist_id = new ObjectId(Utilities.extractDentistId(payload));

                System.out.println("responseTopic" + responseTopic);

                Bson filter = Filters.eq("dentistId", dentist_id);
                ArrayList<Appointment> patientAppointments = collection.find(filter).into(new ArrayList<>());
                // If insertion is acknowledged, publish to response topic
                ArrayList<String> jsonAppointments = new ArrayList<>();
                for (Appointment appointment :  patientAppointments) {
                    String jsonAppointment = appointment.toJSON();
                    jsonAppointments.add(jsonAppointment);
                }

                String resPayload = "[" + String.join(",", jsonAppointments) + "]";

                String mqttResponseTopic = String.format("Dentist/%s/get_appointments/res", responseTopic);
                byte[] messagePayload = resPayload.getBytes();
                MqttMessage publishMessage = new MqttMessage(messagePayload);

                client.publish(mqttResponseTopic, publishMessage);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }));
        client.subscribe(dentistCancelAppointment, 1, (topic, message) -> service.submit(() -> {
            String payload = Utilities.payloadToString(message.getPayload());
            System.out.println("Received message on " + topic + " \nMessage: " + payload);
            try {
                String responseTopic = Utilities.extractResponseTopic(payload);
                ObjectId dentist_id = new ObjectId(Utilities.extractDentistId(payload));
                ObjectId appointment_id = new ObjectId(Utilities.extractAppointmentId(payload));

                Bson filter = Filters.eq("_id", appointment_id);
                Appointment appointment = collection.find(filter).first();

                if (appointment == null) {
                    String mqttResponseTopic = String.format("Dentist/%s/cancel_appointment/res", responseTopic);
                    byte[] messagePayload = new Result(404, "Appointment with given id was not found").toJSON().getBytes();
                    MqttMessage publishMessage = new MqttMessage(messagePayload);
                    client.publish(mqttResponseTopic, publishMessage);
                    return;
                }

                if (!appointment.isBooked()) {
                    String mqttResponseTopic = String.format("Dentist/%s/cancel_appointment/res", responseTopic);
                    byte[] messagePayload = new Result(403, "Appointment with given is not booked").toJSON().getBytes();
                    MqttMessage publishMessage = new MqttMessage(messagePayload);
                    client.publish(mqttResponseTopic, publishMessage);
                    return;
                }

                Document query = new Document("_id", appointment_id);
                Document update = new Document("$set", new Document("patientId", null)
                        .append("booked", false));
                FindOneAndUpdateOptions options = new FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER);
                Appointment updatedDocument = collection.findOneAndUpdate(query, update, options);

                if (updatedDocument != null){
                    String mqttResponseTopic = String.format("Dentist/%s/cancel_appointment/res", responseTopic);
                    byte[] messagePayload = new Result(200, "Appointment was cancelled").toJSON().getBytes();
                    MqttMessage publishMessage = new MqttMessage(messagePayload);
                    client.publish(mqttResponseTopic, publishMessage);
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }));
        // Clinic subscriptions
        client.subscribe(clinicGetAppointments, 1, (topic, message) -> service.submit(() -> {
            String payload = Utilities.payloadToString(message.getPayload());
            try {
                String clinicId = Utilities.extractClinicId(payload);
                String responseTopic = Utilities.extractResponseTopic(payload);

                // Query Appointments based on dentistIds
                Bson filter = Filters.in("clinicId", new ObjectId(clinicId));
                ArrayList<Appointment> matchingAppointments = new ArrayList<>();
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

                String resPayload;
                if(jsonAppointments.isEmpty()) {
                    resPayload = "[" + resultJson + "]";
                }
                else {
                    resPayload = "[" + String.join(", ", jsonAppointments) + "," + resultJson + "]";
                }

                String mqttResponseTopic = String.format("Clinic/%s/get_appointments/res", responseTopic);
                byte[] messagePayload = resPayload.getBytes();
                MqttMessage publishMessage = new MqttMessage(messagePayload);
                client.publish(mqttResponseTopic, publishMessage);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }));

        client.subscribe(deleteDentistAppointments, 1, (topic, message) -> service.submit(() ->{
            String payload = Utilities.payloadToString(message.getPayload());
            System.out.println("Received message on " + topic + " \nMessage: " + payload);
            try {
                String responseTopic = Utilities.extractResponseTopic(payload);
                ObjectId dentist_id = new ObjectId(Utilities.extractDentistId(payload));

                Bson filter = Filters.eq("dentistId", dentist_id);
                DeleteResult result = collection.deleteMany(filter);
                if (result.wasAcknowledged()){
                    String mqttResponseTopic = String.format("Clinic/%s/delete_dentist/res", responseTopic);
                    byte[] resPayloadBytes = new Result(200, "Appointments were deleted successfully.").toJSON().getBytes();
                    MqttMessage response = new MqttMessage(resPayloadBytes);
                    client.publish(mqttResponseTopic, response);
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }));
    }
}
