import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.api.java.JavaRDD;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.spark.SparkConf;

public class SimpleApp {
  public static void main(String[] args) {
    // Set up the Spark configuration and context
    SparkConf conf = new SparkConf().setAppName("Parallel Number Printer").setMaster("local[2]");
    JavaSparkContext sc = new JavaSparkContext(conf);

    // Define the range of numbers to print
    int end = 1000;

    // Create an RDD from a range of numbers (1 to 1000)
    List<Integer> numbersList = java.util.stream.IntStream.rangeClosed(1, end)
        .boxed()
        .collect(Collectors.toList());

    JavaRDD<Integer> numbers = sc.parallelize(numbersList);
    // Print each number in parallel
    numbers.foreach(number -> {
        System.out.println(number);
    });

    // Stop the Spark context
    sc.close();
  }
}

