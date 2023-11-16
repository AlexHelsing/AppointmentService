import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.bson.types.ObjectId;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public class Appointment {


    public Appointment () {}

    @JsonProperty("appointment_id")
    private ObjectId appointmentId;

    @JsonProperty("patient")
    private String patient;

    @JsonProperty("dentist")
    private String dentist;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm")
    private LocalDateTime dateTime;


    public Appointment(String patient, String dentist, LocalDateTime dateTime){
        this.patient = patient;
        this.dentist = dentist;
        this.dateTime = dateTime;
    }

    public ObjectId getAppointmentId() {
        return appointmentId;
    }

    public String getPatient() {
        return patient;
    }

    public void setPatient(String patient) {
        this.patient = patient;
    }

    public String getDentist() {
        return dentist;
    }

    public void setDentist(String dentist) {
        this.dentist = dentist;
    }

    public LocalDateTime getDateTime() {
        return dateTime;
    }

    public void setDateTime(LocalDateTime dateTime) {
        this.dateTime = dateTime;
    }

    public String toString(){
        return String.format("Patient: %s " + "Dentist: %s" + "Date and time: %s ", getPatient(), getDentist(), getDateTime());
    }
}
