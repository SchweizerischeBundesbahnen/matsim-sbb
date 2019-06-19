package ch.ethz.matsim.discrete_mode_choice.Analysis;

/*import ch.ethz.matsim.baseline_scenario.analysis.trips.run.ConvertTripsFromEvents;
import ch.ethz.matsim.baseline_scenario.config.CommandLine;*/

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.*;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigReader;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.core.population.io.PopulationReader;
import org.matsim.core.router.StageActivityTypes;
import org.matsim.core.router.StageActivityTypesImpl;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.collections.Tuple;

import java.io.BufferedWriter;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.stream.Collectors;

import ch.ethz.matsim.discrete_mode_choice.Analysis.CSVReader;


public class OutputAnalysis {
    private static final Logger logger = Logger.getLogger(OutputAnalysis.class);
    private static final String SBB_PREFIX = "CNB.10pct.2015.";
    private static final String OUTPUT_NETWORK = ".output_network.xml.gz";
    private static final String OUTPUT_EVENTS = ".output_events.xml.gz";
    private static final String OUTPUT_PLANS = ".output_plans.xml.gz";
    private static final String OUTPUT_CONFIG = ".output_config.xml";

    private static String outputPath;
    private static String analysisPath;
    private static StageActivityTypes stageActivityTypes;
    
    private static final List<String> analysisIdList = Arrays.asList(new String[]{"CDF","IRTS"});
    
    private static List<File> sceariosDirList;
    private static List<String> analysisList;
    private static String outputCompAnalysisDir;
    private static String outputCompAnalysisId;

    private static void initGlobals() {
        sceariosDirList = new ArrayList<>();
    	analysisList = new ArrayList<>();
    }
    
    private static class ScenarioData{
    	Config config;
    	Scenario scenario;
    	Network network;
    	
    	public ScenarioData(Config config,Scenario scenario,Network network) {
    		this.config = config;
    		this.scenario = scenario;
    		this.network = network;
    	}
    	
    }

    /**
     * provide the path(s) to the output scenarios
     *
     * @param args
     */
    static public void main(String[] args) throws IOException {
    	
    	initGlobals();
    	
    	args2Input(args);
    	
        sceariosDirList.forEach(f -> {
        	
        	String scenarioName = scenarioNameByPath(f);
        	
        	Map<String,File> outputTreeDirectories = outputTreeDir(f);

            System.out.println("\n\nOutput analysis started for: " + f.getAbsolutePath());
            
            ScenarioData sd = scenarioDataImport(f);
      
            analysisList.forEach(analysisId ->{
            	
            	switch (analysisId) {
            	case "CDF":
            		CDF(sd,outputTreeDirectories.get(analysisId),scenarioName);
            		break;
            	case "IRTS":
            		IRTS(f,outputTreeDirectories.get(analysisId),scenarioName);
            		break;
            	}
            });
        });
     
     // comp analysis
        compAnalysis(sceariosDirList.stream().map(x -> x.getAbsolutePath()).collect( Collectors.toList()),analysisList,outputCompAnalysisDir,outputCompAnalysisId);
        
    }

    private static String scenarioNameByPath(File path) {
    	
    	System.out.println( path.getAbsolutePath());
    	List<String> listPath = Arrays.asList(path.getAbsolutePath().split("\\\\"));
    	String name = String.join("-", listPath.subList( listPath.size()-3,listPath.size()));
    	
    	return name;
    	
    }
    
    private static ScenarioData scenarioDataImport(File path) {
    	
    	String outputPath = path.getAbsolutePath() + "/";

        String prefix = Arrays.asList(path.list()).stream().filter(s -> s.contains(OUTPUT_CONFIG)).collect(Collectors.toList()).get(0);
        prefix = prefix.substring(0 , prefix.indexOf("."));
        
        // read scenario
        Config config = ConfigUtils.createConfig();
        new ConfigReader(config).readFile(outputPath + prefix +  OUTPUT_CONFIG);
        Scenario scenario = ScenarioUtils.createScenario(config);
        new PopulationReader(scenario).readFile(outputPath + prefix + OUTPUT_PLANS);
        Network network = NetworkUtils.createNetwork();
        new MatsimNetworkReader(network).readFile(outputPath + prefix + OUTPUT_NETWORK);
    	ScenarioData sd = new ScenarioData(config,scenario,network);
    	return sd;
    }

    private static void args2Input(String[] args) {
    	for(int i = 0;i<args.length;i+=2){
    		String key = args[i];
    		String value = args[i+1];
    		
    		System.out.println(key);
    		System.out.println(value);
    		
    		if(key.equals("--s")) {
    			File dir = new File(value);
    			if(dir.exists()) {
    				sceariosDirList.add(dir);
    			}
    			else {
    				
    			}
    		}
    		else if(key.equals("--a")) {
    			if(analysisIdList.contains(value)) {
    				analysisList.add(value);
    			}
    			else {
    				
    			}
    		}
    		else if(key.equals("--od")) {
    			outputCompAnalysisDir = value;
    		}
    		else if(key.equals("--oid")) {
    			outputCompAnalysisId = value;
    		}
    		else {
    			
    		}
    	}
    	if(sceariosDirList.size()==0 || analysisList.size() ==0) {
    		
    	}
    }
    
    private static Map<String,File> outputTreeDir(File mainDir) {
    	Map<String,File> treeDirMap = new HashMap<String,File>();
    	if(!Arrays.asList(mainDir.list()).contains("OutputAnalysis")) {
    		File analysisOutputDir = new File(mainDir,"OutputAnalysis");
    		analysisOutputDir.mkdir();
    		analysisList.forEach(s->{
    			File analysisDir = new File(analysisOutputDir,s.toString());
    			analysisDir.mkdir();
    			treeDirMap.put(s.toString(),analysisDir);
    		});
    	}
    	else {
    		
    	}
    	return treeDirMap;
    }
    
    private static void CDF(ScenarioData sd,File f,String name) {
      // do analysis
      // get statistical values of best scores
//      List<Double> bestScoresOfPeople = sd.scenario.getPopulation().getPersons().values().stream().mapToDouble(
//              person -> person.getPlans().stream().mapToDouble(BasicPlan::getScore).max().getAsDouble()
//      ).boxed().collect(Collectors.toList());
      
      HashMap<String,Double> result = new HashMap<String,Double>();
    	
      for (Person person : sd.scenario.getPopulation().getPersons().values()) {
    	  result.put(person.getId().toString(),person.getPlans().stream().mapToDouble(BasicPlan::getScore).max().getAsDouble());
      }
      
      BufferedWriter writer = null;
      try 
      {
    	  writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(f.getAbsolutePath()+"/CDF.csv")));
    	  //head
    	  writer.write(String.join(";","personId_"+name,"finalPlanScore_"+name) + "\n");
    	  for(Map.Entry<String,Double> entry : result.entrySet()) {
    		    String key = entry.getKey();
    		    String value = entry.getValue().toString();
    		    writer.write(String.join(";",key,value) + "\n");
    	  }
      }
      catch (IOException e) {
	        e.printStackTrace();
      } finally {
    	  close(writer);
      }
	     
      

    }
    
    //iteration time vs score
    private static void IRTS(File inputF,File outputF,String name){
    	LinkedHashMap<String,List<Date>> sw = CSVReader.readFromCSVstopwatch(inputF.getAbsolutePath() +"/Baseline.stopwatch.txt");
    	LinkedHashMap<String,List<Double>> st =  CSVReader.readFromCSVscorestat(inputF.getAbsolutePath()+"/Baseline.scorestats.txt");
    
    	int numOfIter = sw.get("iteration").size();
    	
    	BufferedWriter writer = null;
        try 
        {
      	  writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outputF.getAbsolutePath()+"/IRTS.csv")));
      	  writer.write(String.join(";","iteration","iterationTimeSec_"+name,"averagePlanScore_"+name) + "\n");
      	  for(int i =0;i<numOfIter;i++) {
      		    Date currDate = sw.get("iteration").get(i);
      		    String iterTime = Integer.toString(currDate.getSeconds()+currDate.getMinutes() * 60 +currDate.getHours()*3600);
      		    String score = Double.toString(st.get("avg. EXECUTED").get(i));
      		    writer.write(String.join(";",Integer.toString(i+1) ,iterTime,score) + "\n");
      	  }
        }
        catch (IOException e) {
  	        e.printStackTrace();
        } finally {
      	  close(writer);
        }
    }
    
    
    /*private static void writeTripsToCSV() {
        String tripsFilename = "trips.csv";
        try {
            ConvertTripsFromEvents.main(new String[]{outputPath + OUTPUT_NETWORK, outputPath + OUTPUT_EVENTS, outputPath + tripsFilename});
        } catch (IOException e) {
            e.printStackTrace();
        }
    }*/

//    private static void assignFields(List<? extends Number> vals, List<String> names) {
//        assert vals.size() == names.size();
//        IntStream.range(0, vals.size()).forEach(value -> {
//            try {
//                OutputAnalysisRow.class.getField(names.get(value)).set(row, vals.get(value));
//            } catch (IllegalAccessException | NoSuchFieldException e) {
//                e.printStackTrace();
//            }
//        });
//    }

//    private static Collection<TripItem> getTripItems(Network network, String outputPath) {
//        HomeActivityTypes homeActivityTypes = new BaselineHomeActivityTypes();
//        MainModeIdentifier mainModeIdentifier = new MainModeIdentifierImpl();
//        Collection<String> networkRouteModes = Arrays.asList(TransportMode.car, "ride");
//
//        TripListener tripListener = new TripListener(network, stageActivityTypes, homeActivityTypes, mainModeIdentifier, networkRouteModes);
//        return new EventsTripReader(tripListener).readTrips(OutputAnalysis.outputPath + OUTPUT_EVENTS);
//    }


    public static void close(Closeable closeable) {
        if (closeable == null) return;
        try {
            closeable.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static StageActivityTypes getStageActivityTypes(Collection<? extends Person> persons) {

        Set<String> interactionTypes = new HashSet<>();
        for (Person person : persons) {
            for (Plan plan : person.getPlans()) {
                for (PlanElement element : plan.getPlanElements()) {
                    if (element instanceof Activity) {
                        Activity activity = (Activity) element;
                        if (activity.getType().contains("interaction")) {
                            interactionTypes.add(activity.getType());
                        }
                    }
                }
            }
        }
        return new StageActivityTypesImpl(new ArrayList<>(interactionTypes));
    }
    
    private static boolean compAnalysis(List<String> inputPaths, List<String> analysis, String outputPath, String ouputAnalysisId) {
    	// create the output analysis folders tree
    	Map<String,File> treeDirMap = new HashMap<String,File>();
    	File mainDir = new File(outputPath);
    	if(!Arrays.asList(mainDir.list()).contains(ouputAnalysisId)) {
    		File analysisOutputDir = new File(mainDir,ouputAnalysisId);
    		analysisOutputDir.mkdir();
    		analysis.forEach(s->{
    			File analysisDir = new File(analysisOutputDir,s.toString());
    			analysisDir.mkdir();
    			treeDirMap.put(s.toString(),analysisDir);
    		});
    	}
    	else {
    		return false;
    	}
    	analysis.forEach(s->{
    		List<List<List<String>>> allRecords = new ArrayList<>();
    		inputPaths.forEach(f->{
    			List<List<String>> records = new ArrayList<>();
    			System.out.println(f.toString()+"//OutputAnalysis//"+s.toString()+"//"+s.toString()+".csv");
    			try(FileReader dir = new FileReader(f.toString()+"//OutputAnalysis//"+s.toString()+"//"+s.toString()+".csv")) {
	    			try (BufferedReader br = new BufferedReader(dir)) {
	    			    String line;
	    			    while ((line = br.readLine()) != null) {
	    			        String[] values = line.split(";");
	    			        records.add(Arrays.asList(values));
	    			    }
	    			}
	    			allRecords.add(records);
    			}
    			catch (IOException ex) {
    			    System.out.println("ciao");
    			}
    			
    		});
    		System.out.println(allRecords.size());
    		BufferedWriter writer = null;
			try 
		      {
		    	  writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outputPath+"//"+ouputAnalysisId+"//"+s+"//"+s+".csv")));
		    	  //head
		    	  
		    	  List<String> head = new ArrayList<>(allRecords.get(0).get(0));
		    	  
		    	  System.out.println(String.join(";",head));
		    	  for(int j=1;j<allRecords.size();j++) {
		    		  for(int i =1;i<allRecords.get(j).get(0).size();i++) {
		    			  System.out.println(allRecords.get(j).get(0).get(i).toString());
		    			  head.add(allRecords.get(j).get(0).get(i).toString());
		    		  }
		    	  }
		    	  System.out.println(String.join(";",head));
		    	  writer.write(String.join(";",head) + "\n");
		    	  
		    	  //find the min 
		    	  int minimum = Integer.MAX_VALUE;
		    	  for(List<List<String>> ls: allRecords) {
		    		  minimum = (minimum > ls.size())? ls.size() : minimum;
		    	  }
		    	  
		    	  for(int j = 1;j<minimum;j++) {
		    		  List<String> values = new ArrayList<>();
		    		 for(int i = 0;i<allRecords.size();i++) {
		    			 for(int k = 0;k<allRecords.get(i).get(j).size();k++) {
		    				 if(k == 0) {
		    					 if(i==0) {
		    						 values.add(allRecords.get(i).get(j).get(k));
		    					 }
		    				 }
		    				 else {
		    					 values.add(allRecords.get(i).get(j).get(k));
		    				 }
		    			    
		    			 }
		    		 }
		    		 writer.write(String.join(";",values) + "\n");
		    	  }
		      }
		      catch (IOException e) {
			        e.printStackTrace();
		      } finally {
		    	  close(writer);
		      }
    	});
    	return true;
    	//create the output files foreach anlysis
    }
}

