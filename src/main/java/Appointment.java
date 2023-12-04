import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.bson.Document;

import java.time.LocalDate;
import java.time.LocalTime;

public class Appointment  {

    public Appointment () {}

    @JsonProperty("patientId")
    private String patientId;

    @JsonProperty("dentistId")
    private String dentistId;

    //@JsonProperty("clinicId")
    //private String clinicId;

    @JsonProperty("isBooked")
    private boolean isBooked;

    @JsonProperty("date")
    private LocalDate date;

    @JsonFormat(pattern = "HH:mm")
    private LocalTime startTime;
    @JsonFormat(pattern = "HH:mm")
    private LocalTime endTime;


    public Appointment(String patientId, String dentistId, boolean isBooked, LocalDate date, LocalTime startTime, LocalTime endTime){
        this.patientId = patientId;
        this.dentistId = dentistId;
        //this.clinicId = clinicId;
        this.isBooked = isBooked;
        this.date = date;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    public String getPatientId() {
        return patientId;
    }

    public void setPatientId(String patientId) {
        this.patientId = patientId;
    }

    public String getDentistId() {
        return dentistId;
    }

    public void setDentistId(String dentistId) {
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

    public String toString(){
        return String.format("""
                Patient: %s\s
                Dentist: %s\s
                Date: %s Starting at: %s Ending at: %s""", getPatientId(), getDentistId(), getDate(), getStartTime(), getEndTime());
    }

    public String toJSON() {
        GsonBuilder gsonBuilder = new GsonBuilder();
        gsonBuilder.setPrettyPrinting();
        Gson gson = gsonBuilder.create();
        return gson.toJson(this);
    }
}
