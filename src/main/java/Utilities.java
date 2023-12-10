import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

import java.lang.reflect.Type;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

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

                appointment.setClinicId(new ObjectId());
                appointment.setBooked(false);
                String clinicId_string  = element.get("clinic_id").asText();
                ObjectId clinicId = new ObjectId(clinicId_string);
                appointment.setClinicId(clinicId);
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
        System.out.println("Test");
        JsonNode jsonNode = objectMapper.readTree(payload);
        System.out.println(jsonNode);

        ArrayList<ObjectId> dentistIds = new ArrayList<>();

        // Only checks for DentistId:s
        if(jsonNode.has("dentists")){
            JsonNode dentistArray = jsonNode.get("dentists");
            for (JsonNode dentistNode:dentistArray) {
                String id = dentistNode.asText();
                // Check for hexstring length
                if(id.length() == 24){
                    ObjectId objectId = new ObjectId(id);
                    dentistIds.add(objectId);
        }}}


        return dentistIds;
    }

    public static String extractResponseTopic(String payload) throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode jsonNode = objectMapper.readTree(payload);

        String responseTopic = "";

        if (jsonNode.isArray()) {
            JsonNode lastElement = jsonNode.get(jsonNode.size() - 1);
            responseTopic = lastElement.get("responseTopic").asText();
        } else if (jsonNode.isObject()) {
            responseTopic = jsonNode.get("responseTopic").asText();
        }

        return responseTopic;
    }

    public static Bson createClinicIdFilter(String clinicId) {
        return Filters.eq("clinicId", new ObjectId(clinicId));
    }

    public static Bson createClinicIdsFilter(List<ObjectId> clinicIds) {
        LocalDate currentDate = LocalDate.now();
        // Last filter is for only fetching appointments with times from the current date and forward
        // Since being able to book appointments from the past makes no sense
        return Filters.and(
                Filters.in("clinicId", clinicIds),
                Filters.eq("isBooked", false),
                Filters.gte("date", currentDate)
        );
    }

    public static ArrayList<ObjectId> convertToClinicIds (String payload) throws JsonProcessingException {
        ObjectMapper objectMapper = new ObjectMapper();
        System.out.println("Test");
        JsonNode jsonNode = objectMapper.readTree(payload);
        System.out.println(jsonNode);

        ArrayList<ObjectId> clinicIds = new ArrayList<>();

        // Check if "clinics" field exists in the JSON
        if (jsonNode.has("clinics") && jsonNode.get("clinics").isArray()) {
            JsonNode clinicsArray = jsonNode.get("clinics");
            for (JsonNode clinicNode : clinicsArray) {
                String clinicId = clinicNode.asText();
                // Check for hexstring length
                if (clinicId.length() == 24) {
                    ObjectId objectId = new ObjectId(clinicId);
                    clinicIds.add(objectId);
                }
            }
        }
        return clinicIds;
    }

    public static String extractClinicId(String payload) throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode jsonNode = objectMapper.readTree(payload);

        // Check if "clinics" field exists in the JSON
        if (jsonNode.has("clinics")) {
            String clinicId = jsonNode.get("clinics").asText();

            // Check for hexstring length
            if (clinicId.length() == 24) {
                return clinicId;
            }
        }

        // If clinicId is not found or has an invalid length, return null or throw an exception
        return null;
    }

    public static List<ObjectId> extractClinicIds(String payload) throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode jsonNode = objectMapper.readTree(payload);

        List<ObjectId> clinicIds = new ArrayList<>();

        if (jsonNode.has("clinicIds")) {
            JsonNode clinicIdsNode = jsonNode.get("clinicIds");

            if (clinicIdsNode.isArray()) {
                for (JsonNode clinicIdNode : clinicIdsNode) {
                    String clinicId = clinicIdNode.asText();

                    // Check for hexstring length
                    if (clinicId.length() == 24) {
                        ObjectId objectId = new ObjectId(clinicId);
                        clinicIds.add(objectId);
                    }
                }
            }
        }

        return clinicIds;
    }


    // SINGULAR USE THIS FOR THE clinicGetAppointmentsDate only. Will fix but headache trying to parse the god damn json
    // crap
    public static ArrayList<ObjectId> convertToClinicId(String payload) throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        System.out.println("Test");
        JsonNode jsonNode = objectMapper.readTree(payload);
        System.out.println(jsonNode);

        ArrayList<ObjectId> clinicIds = new ArrayList<>();

        // Check if "clinics" field exists in the JSON
        if (jsonNode.has("clinics")) {
            String clinicId = jsonNode.get("clinics").asText();

            // Check for hexstring length
            if (clinicId.length() == 24) {
                ObjectId objectId = new ObjectId(clinicId);
                clinicIds.add(objectId);
            }
        }

        return clinicIds;
    }


    public static LocalDate extractDate(String payload) throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode jsonNode = objectMapper.readTree(payload);

        String date = "";

        if (jsonNode.isArray()) {
            JsonNode lastElement = jsonNode.get(jsonNode.size() - 1);
            date = lastElement.get("date").asText();
        } else if (jsonNode.isObject()) {
            date = jsonNode.get("date").asText();
        }

        return LocalDate.parse(date);
    }

    public static MongoCollection<Appointment> getCollection(MongoClient mongoClient) {
        MongoDatabase database = mongoClient.getDatabase("appointments").withCodecRegistry(MqttMain.pojoCodecRegistry);
        return database.getCollection("appointments", Appointment.class);
    }
}
