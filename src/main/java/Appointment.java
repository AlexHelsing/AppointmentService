import org.bson.types.ObjectId;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Date;

public class Appointment {


    public Appointment () {}

    private ObjectId appointmentId;

    private String patient;

    private String dentist;

    private LocalDate date;

    private LocalTime time;

    public Appointment(String patient, String dentist, LocalDate date, LocalTime time){
        this.patient = patient;
        this.dentist = dentist;
        this.date = date;
        this.time = time;
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

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public LocalTime getTime() {
        return time;
    }

    public void setTime(LocalTime time) {
        this.time = time;
    }
}
