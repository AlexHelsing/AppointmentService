import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import org.bson.codecs.pojo.annotations.BsonProperty;
import org.bson.types.ObjectId;

import java.time.LocalDate;
import java.time.LocalTime;

public class Appointment {
    private ObjectId _id;
    private ObjectId patientId;

    private ObjectId dentistId;

    private ObjectId clinicId;

    private boolean booked;

    private LocalDate date;

    private LocalTime startTime;

    private LocalTime endTime;

    public Appointment() {
    }

    public Appointment(ObjectId patientId, ObjectId dentistId, ObjectId clinicId, boolean booked, LocalDate date,
            LocalTime startTime, LocalTime endTime) {
        this.patientId = patientId;
        this.dentistId = dentistId;
        this.clinicId = clinicId;
        this.booked = booked;
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

    public ObjectId getClinicId() {
        return clinicId;
    }

    public void setClinicId(ObjectId clinicId) {
        this.clinicId = clinicId;
    }

    public boolean isBooked() {
        return this.booked;
    }

    public void setBooked(boolean booked) {
        this.booked = booked;
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
                Clinic: %s\s
                isBooked: %s\s
                Date: %s Starting at: %s Ending at: %s""", getPatientId(), getDentistId(), getClinicId(), isBooked(),
                getDate(),
                getStartTime(),
                getEndTime());
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
