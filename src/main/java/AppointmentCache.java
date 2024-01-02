import com.google.gson.Gson;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class AppointmentCache {

    private static JedisPool jedisPool;
    private Gson gson;


    static {
        // Initialize the JedisPoolConfig
        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(50); // for example, set the max total connections
        poolConfig.setMaxIdle(10);  // for example, set the max idle connections

        // Initialize the JedisPool using the config and the Redis connection details
        jedisPool = new JedisPool(poolConfig, "127.0.0.1", 6379);
    }

    public AppointmentCache() {
        this.gson = new Gson();
    }

    public List<Appointment> getClinicAppointments(String clinicId) {
        try (Jedis jedis = jedisPool.getResource()) {
            String keyPattern = "appointment:" + clinicId;
            Set<String> keys = jedis.keys(keyPattern);
            System.out.println("Redis test1");

            if (!keys.isEmpty()) {
                return keys.stream()
                        .map(key -> gson.fromJson(jedis.get(key), Appointment.class))
                        .collect(Collectors.toList());
            } else {
                return Collections.emptyList();
            }
        } catch (Exception e) {
            e.printStackTrace();
            // Consider how you want to handle exceptions - perhaps rethrowing or handling more gracefully
        }
        return Collections.emptyList(); // Return empty list in case of error
    }


    public void cacheAppointment(Appointment appointment) {
        try (Jedis jedis = jedisPool.getResource()) {
            String appointmentJson = gson.toJson(appointment);
            System.out.println(appointmentJson);
            String key = "appointment:" + appointment.getClinicId().toString();
            jedis.set(key, appointmentJson);
        } catch (Exception e) {
            e.printStackTrace();
            // Handle exception appropriately
        }
    }

}

