import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.types.ObjectId;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;

import static java.nio.charset.StandardCharsets.UTF_8;

public class Utilities {

    public static String payloadToString(byte[] payload) {
        return new String(payload, UTF_8);
    }

    // Received payload is expected to be a string formatted valid json object of json array.
    public static ArrayList<Appointment> convertToAppointments(String payload) throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode jsonNode = objectMapper.readTree(payload);

        ArrayList<Appointment> appointments = new ArrayList<>();
        // Creating appointment objects
        if (jsonNode.isArray()) {
            // Last element holds the response_topic
            for (int i = 0; i < jsonNode.size() - 1 ; i++) {
                Appointment appointment = new Appointment();
                JsonNode element = jsonNode.get(i);

                appointment.setId(new ObjectId());
                appointment.setBooked(false);
                String dentistId_string  = element.get("dentist_id").asText();
                ObjectId dentistId = new ObjectId(dentistId_string);
                appointment.setDentistId(dentistId);
                // Appointment Date
                String dateString = element.get("date").asText();
                LocalDate date = LocalDate.parse(dateString, DateTimeFormatter.ISO_LOCAL_DATE);
                appointment.setDate(date);
                // Appointment Start time
                String startString = element.get("start").asText();
                LocalTime startTime = LocalTime.parse(startString);
                appointment.setStartTime(startTime);
                //Appointment End time
                String endString = element.get("end").asText();
                LocalTime endTime = LocalTime.parse(endString);
                appointment.setEndTime(endTime);

                appointments.add(appointment);
            }
        }
        else {
            throw new Exception("Received payload is not formatted as an Array.");
        }

        return appointments;
    }

    public static ArrayList<ObjectId> convertToDentistIds (String payload) throws JsonMappingException, JsonProcessingException {
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode jsonNode = objectMapper.readTree(payload);

        ArrayList<ObjectId> dentistIds = new ArrayList<>();

        // Last item contains response_topic
        for (int i = 0; i < jsonNode.size() - 1; i++) {
            String id = jsonNode.get(i).asText();
            ObjectId objectId = new ObjectId(id);
            dentistIds.add(objectId);
        }

        return dentistIds;
    }

    public static String extractResponseTopic(String payload) throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode jsonNode = objectMapper.readTree(payload);

        String responseTopic = "";

        if (jsonNode.isArray()) {
            JsonNode lastElement = jsonNode.get(jsonNode.size() - 1);
            responseTopic = lastElement.get("response_topic").asText();
        } else {
            responseTopic = jsonNode.get("response_topic").asText();
        }

        return responseTopic;
    }

    public static String extractPatientId(String payload) throws JsonProcessingException {
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode jsonNode = objectMapper.readTree(payload);

        return jsonNode.get("patient_id").asText();
    }

    public static String extractClinicId(String payload) throws JsonProcessingException {
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode jsonNode = objectMapper.readTree(payload);

        return jsonNode.get("clinicId").asText();
    }

    public  static String extractAppointmentId(String payload) throws JsonProcessingException {
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode jsonNode = objectMapper.readTree(payload);

        return jsonNode.get("appointment_id").asText();
    }

    public  static String extractDentistId(String payload) throws JsonProcessingException {
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode jsonNode = objectMapper.readTree(payload);

        return jsonNode.get("dentist_id").asText();
    }
    public static MongoCollection<Appointment> getCollection(MongoClient mongoClient) {
        MongoDatabase database = mongoClient.getDatabase("Appointments").withCodecRegistry(MqttMain.pojoCodecRegistry);
        return database.getCollection("appointments", Appointment.class);
    }
}
