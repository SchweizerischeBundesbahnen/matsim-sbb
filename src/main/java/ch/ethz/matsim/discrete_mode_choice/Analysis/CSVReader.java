package ch.ethz.matsim.discrete_mode_choice.Analysis;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.*;
import java.util.Date;
import java.text.SimpleDateFormat;
import java.text.ParseException;


public class CSVReader {
	
	static public void main(String[] args) throws IOException {
		LinkedHashMap<String,List<Date>> ll = readFromCSVstopwatch("C:/Users/spenazzi/Projects/sbb/SBB/output/baseline/SMC/Baseline.stopwatch.txt");
		//LinkedHashMap<String,List<Double>> ll = readFromCSVscorestat("C:/Users/spenazzi/Projects/sbb/SBB/output/baseline/SMC/Baseline.scorestats.txt");
//		ll.get("avg. EXECUTED").forEach(s->{
//			System.out.println(Double.toString(s));
//		});
	}
	
	public static LinkedHashMap<String,List<Date>> readFromCSVstopwatch(String fileName) {
		LinkedHashMap<String,List<Date>> output = new LinkedHashMap<String,List<Date>>();
		Path pathToFile = Paths.get(fileName);
		SimpleDateFormat formatter = new SimpleDateFormat("HH:mm:ss");
		
		// create an instance of BufferedReader
		// using try with resource, Java 7 feature to close resources 
		try (BufferedReader br = Files.newBufferedReader(pathToFile, StandardCharsets.US_ASCII))
		{ 
			// read the first line from the text file
			String line = br.readLine();
			
			//output.put("Iteration", new ArrayList<>());
//			output.put("BEGIN iteration", new ArrayList<>());
//			output.put("BEGIN iterationStartsListeners", new ArrayList<>());
//			output.put("END iterationStartsListeners", new ArrayList<>());
//			output.put("BEGIN replanning", new ArrayList<>());
//			output.put("END replanning", new ArrayList<>());
//			output.put("BEGIN beforeMobsimListeners", new ArrayList<>());
//			output.put("BEGIN dump all plans", new ArrayList<>());
//			output.put("END dump all plans", new ArrayList<>());
//			output.put("END beforeMobsimListeners", new ArrayList<>());
//			output.put("BEGIN prepareForMobsim", new ArrayList<>());
//			output.put("END prepareForMobsim", new ArrayList<>());
//			output.put("BEGIN mobsim", new ArrayList<>());
//			output.put("END mobsim", new ArrayList<>());
//			output.put("BEGIN afterMobsimListeners", new ArrayList<>());
//			output.put("END afterMobsimListeners", new ArrayList<>());
//			output.put("BEGIN scoring", new ArrayList<>());
//			output.put("END scoring", new ArrayList<>());
//			output.put("BEGIN iterationEndsListeners", new ArrayList<>());
//			output.put("BEGIN compare with counts", new ArrayList<>());
//			output.put("END compare with counts", new ArrayList<>());
//			output.put("END iterationEndsListeners", new ArrayList<>());
//			output.put("END iteration", new ArrayList<>());
//			output.put("iterationStartsListeners", new ArrayList<>());
//			output.put("replanning", new ArrayList<>());
//			output.put("dump all plans", new ArrayList<>());
//			output.put("beforeMobsimListeners", new ArrayList<>());
//			output.put("prepareForMobsim", new ArrayList<>());
//			output.put("mobsim", new ArrayList<>());
//			output.put("afterMobsimListeners", new ArrayList<>());
//			output.put("scoring", new ArrayList<>());
//			output.put("compare with counts", new ArrayList<>());
//			output.put("iterationEndsListeners", new ArrayList<>());
//			output.put("iteration", new ArrayList<>());
			
			List<String> headRow = Arrays.asList(line.split("\\t"));
			headRow.forEach(s -> {
				output.put(s, new ArrayList<>());
			});
			
			
			Set<String> keys = output.keySet();
			line = br.readLine();
			// loop until all lines are read
			while (line != null) { 
				int iterator = 0;
				List<String> dateRow = Arrays.asList(line.split("\\t"));
				for(String k:keys){
					try {
						if(iterator>0) 
						{
							Date date = null;
							if( dateRow.get(iterator).length() > 0) {
								date = formatter.parse(dateRow.get(iterator));
							}
							output.get(k).add(date);
							 if(date == null) { System.out.println(k + " = "+ "" );}
							 else {System.out.println(k + " = "+ date.toString());}
						}
						iterator++;
					} catch (ParseException e) {
			            e.printStackTrace();
			        }
				}
				System.out.println();
				line = br.readLine(); 
				}
		} catch (IOException ioe) {
			ioe.printStackTrace(); 
		}
	return output; 
	}
	
	
	public static LinkedHashMap<String,List<Double>> readFromCSVscorestat(String fileName) {
		LinkedHashMap<String,List<Double>> output = new LinkedHashMap<String,List<Double>>();
		Path pathToFile = Paths.get(fileName);
		
		
		// create an instance of BufferedReader
		// using try with resource, Java 7 feature to close resources 
		try (BufferedReader br = Files.newBufferedReader(pathToFile, StandardCharsets.US_ASCII))
		{ 
			// read the first line from the text file
			String line = br.readLine();
			
			List<String> headRow = Arrays.asList(line.split("\\t"));
			headRow.forEach(s -> {
				output.put(s, new ArrayList<>());
			});
			Set<String> keys = output.keySet();
			
			line = br.readLine();
			// loop until all lines are read
			while (line != null) { 
				int iterator = 0;
				List<String> dateRow = Arrays.asList(line.split("\\t"));
				for(String k:keys){
					double value = -9999999;
					if( dateRow.get(iterator).length() > 0) {
						
						value = Double.parseDouble(dateRow.get(iterator));
					}
					System.out.println(Double.toString(value));
					output.get(k).add(value);
					iterator++;
		        }
				
				// adding book into ArrayList 
				
				// read next line before looping
				// if end of file reached, line would be null
				line = br.readLine(); 
				}
		} catch (IOException ioe) {
			ioe.printStackTrace(); 
		}
	return output; 
	}

}
