import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;

public class Utilities {
    // Received payload is expected to be a string formatted valid json object of json array.
    public static ArrayList<Appointment> convertToAppointmentArray(String payload) throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode jsonNode = objectMapper.readTree(payload);

        ArrayList<Appointment> appointments = new ArrayList<>();
        // Creating appointment objects
        if (jsonNode.isArray()) {
            // Last element holds the response_topic
            for (int i = 0; i < jsonNode.size() - 1 ; i++) {
                Appointment appointment = new Appointment();
                JsonNode element = jsonNode.get(i);

                appointment.setBooked(false);
                appointment.setDentistId(element.get("dentist_id").asText());
                // Appointment Date
                String dateString = element.get("date").asText();
                LocalDate date = LocalDate.parse(dateString);
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

    public static String extractResponseTopic(String payload) throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode jsonNode = objectMapper.readTree(payload);

        String responseTopic = "";

        if (jsonNode.isArray()) {
            JsonNode lastElement = jsonNode.get(jsonNode.size() - 1);
            responseTopic = lastElement.get("response_topic").asText();
        } else if (jsonNode.isObject()) {
            responseTopic = jsonNode.get("response_topic").asText();
        }

        return responseTopic;
    }

    public static MongoCollection<Appointment> getCollection() {
        try (MongoClient mongoClient = MongoClients.create(AppConfig.getMongodbUri())) {
            MongoDatabase database = mongoClient.getDatabase("appointments").withCodecRegistry(MqttMain.pojoCodecRegistry);
            mongoClient.listDatabases().forEach(System.out::println);
            database.listCollectionNames().forEach(System.out::println);
            return database.getCollection("appointments", Appointment.class);
        }

    }


}
