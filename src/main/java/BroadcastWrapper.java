import java.util.Map;
import org.apache.spark.SparkContext;
import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.broadcast.Broadcast;

public class BroadcastWrapper {

    private Broadcast<Map<String, String>> broadcastVar;
    private static BroadcastWrapper obj = new BroadcastWrapper();

    private BroadcastWrapper(){}

    public static BroadcastWrapper getInstance() {
        return obj;
    }

    public JavaSparkContext getSparkContext(SparkContext sc) {
       JavaSparkContext jsc = JavaSparkContext.fromSparkContext(sc);
       return jsc;
    }

    // The method now directly updates the broadcasted variable
    public void updateAndGet(SparkContext sparkContext, Map<String, String> newData) {
        // If a previous broadcast exists, unpersist it
        if (broadcastVar != null) {
            broadcastVar.unpersist();
        }
        // Broadcast the new data
        broadcastVar = getSparkContext(sparkContext).broadcast(newData);
    }
}

