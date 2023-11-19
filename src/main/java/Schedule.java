import java.util.ArrayList;
import java.util.List;

public class Schedule {

    private List<Appointment> appointments;

    public Schedule(){
        this.appointments = new ArrayList<>();
    }

    public void addSlot(Appointment appointment) {
        appointments.add(appointment);
    }

    public List<Appointment> getSlots() {
        return appointments;
    }

    @Override
    public String toString() {
        StringBuilder scheduleString = new StringBuilder();
        for (Appointment appointment : appointments) {
            scheduleString.append(appointment.toString()).append("\n");
        }
        return scheduleString.toString();
    }
}

