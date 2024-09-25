package bdt.df;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.HColumnDescriptor;
import org.apache.hadoop.hbase.HTableDescriptor;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Admin;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.ConnectionFactory;
import org.apache.hadoop.hbase.client.Get;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.Table;
import org.apache.hadoop.hbase.io.compress.Compression.Algorithm;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaPairRDD;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.storage.StorageLevel;
import org.apache.spark.streaming.Durations;
import org.apache.spark.streaming.api.java.JavaDStream;
import org.apache.spark.streaming.api.java.JavaInputDStream;
import org.apache.spark.streaming.api.java.JavaPairDStream;
import org.apache.spark.streaming.api.java.JavaStreamingContext;
import org.apache.spark.streaming.kafka010.ConsumerStrategies;
import org.apache.spark.streaming.kafka010.KafkaUtils;
import org.apache.spark.streaming.kafka010.LocationStrategies;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONObject;

import scala.Tuple2;

import org.apache.spark.sql.*;
public class DataFlow {
	
	private static final String TABLE_NAME = "stock";
	private static final String CF = "stock_data";
	
	public static void main(String[] args) throws Exception
	{
		Configuration hbaseConfig = HBaseConfiguration.create();
		
		try (Connection connection = ConnectionFactory.createConnection(hbaseConfig);
			 Admin admin = connection.getAdmin())
		{
			
			HTableDescriptor table = new HTableDescriptor(TableName.valueOf(TABLE_NAME));
			table.addFamily(new HColumnDescriptor(CF).setCompressionType(Algorithm.NONE));

			if (!admin.tableExists(table.getTableName()))
			{
				admin.createTable(table);
				System.out.print("Table created");
			}
			
			SparkConf conf = new SparkConf().setMaster("local[2]").setAppName("BDTDataFlow");
			JavaStreamingContext ctx = new JavaStreamingContext(conf, Durations.seconds(5));
			
			
			Map<String, Object> kafkaParams = new HashMap<>();
	        kafkaParams.put("bootstrap.servers", "192.168.56.1:9092");
	        kafkaParams.put("key.deserializer", StringDeserializer.class);
	        kafkaParams.put("value.deserializer", StringDeserializer.class);
	        kafkaParams.put("group.id", "bdt-group");

	        // Create Kafka direct stream
	        Collection<String> topics = Arrays.asList("bdt-topic");

	        JavaInputDStream<ConsumerRecord<String, String>> kafkaStream =
	          KafkaUtils.createDirectStream(
	        	ctx,
	            LocationStrategies.PreferConsistent(),
	            ConsumerStrategies.<String, String>Subscribe(topics, kafkaParams)
	          );
	        
	        List<String> filterList = Arrays.asList("BTCUSD","ETHUSD","USDTUSD");
	        
	        JavaDStream<String> stream = kafkaStream.map(record -> new JSONObject(record.value()))
		        .filter(f -> filterList.contains(f.getString("symbol")))
		        .map(json -> json.toString());
	        
	        stream.foreachRDD(data -> {    
	        	data.collect().forEach(json -> {
	        		try {
	        			
	        			
	        			System.out.println(">>>>>>>>>> " + json);
	        			
	        			
						JSONObject stock = new JSONObject(json);
			        	String rowKey = stock.getString("symbol");;
		    			Get row = new Get(Bytes.toBytes(rowKey));
		    			
		    			Table tbl = connection.getTable(TableName.valueOf(TABLE_NAME));
		    			Put newStock = new Put(Bytes.toBytes(rowKey));		        		
		    			newStock.addColumn(Bytes.toBytes(CF), Bytes.toBytes("info"), Bytes.toBytes(stock.toString()));
		    			LocalDateTime now = LocalDateTime.now();
		    	        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
		    	        String formattedDateTime = now.format(formatter);
		    			newStock.addColumn(Bytes.toBytes(CF), Bytes.toBytes("lastUpdated"), Bytes.toBytes(formattedDateTime));
		    			tbl.put(newStock);
					} catch (Exception e) {						
						e.printStackTrace();
					}
	        	});
	        });
	        
			ctx.start();
	        ctx.awaitTermination();
		}
		catch(Exception ex)
		{
			ex.printStackTrace();
		}
	}

}
