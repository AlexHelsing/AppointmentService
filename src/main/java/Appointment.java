import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.bson.types.ObjectId;

import java.time.LocalDate;
import java.time.LocalTime;

public class Appointment {
    private ObjectId _id;
    private ObjectId patientId;

    private ObjectId dentistId;

    //@JsonProperty("clinicId")
    //private String clinicId;

    private boolean isBooked;

    private LocalDate date;

    private LocalTime startTime;

    private LocalTime endTime;

    public Appointment() {}
    public Appointment(ObjectId patientId, ObjectId dentistId, boolean isBooked, LocalDate date, LocalTime startTime, LocalTime endTime) {
        this.patientId = patientId;
        this.dentistId = dentistId;
        //this.clinicId = clinicId;
        this.isBooked = isBooked;
        this.date = date;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    public ObjectId getPatientId() {
        return patientId;
    }

    public void setPatientId(ObjectId patientId) {
        this.patientId = patientId;
    }

    public ObjectId getDentistId() {
        return dentistId;
    }

    public void setDentistId(ObjectId dentistId) {
        this.dentistId = dentistId;
    }

    public boolean isBooked() {
        return isBooked;
    }

    public void setBooked(boolean booked) {
        isBooked = booked;
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalTime startTime) {
        this.startTime = startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalTime endTime) {
        this.endTime = endTime;
    }

    public ObjectId getId() {
        return this._id;
    }

    public void setId(ObjectId id) {
        this._id = id;
    }
    public String toString() {
        return String.format("""
                Patient: %s\s
                Dentist: %s\s
                Date: %s Starting at: %s Ending at: %s""", getPatientId(), getDentistId(), getDate(), getStartTime(), getEndTime());
    }

    public String toJSON() {
        GsonBuilder gsonBuilder = new GsonBuilder();
        gsonBuilder.setPrettyPrinting();
        Gson gson = gsonBuilder
                .registerTypeAdapter(ObjectId.class, new MongoObjectIdTypeAdapter())
                .registerTypeAdapter(LocalDate.class, new LocalDateTypeAdapter())
                .registerTypeAdapter(LocalTime.class, new LocalTimeTypeAdapter())
                .create();
        return gson.toJson(this);
    }
}
