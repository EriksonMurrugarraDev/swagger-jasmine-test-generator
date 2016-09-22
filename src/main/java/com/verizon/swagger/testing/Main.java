package com.verizon.swagger.testing;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

public class Main {

	StringBuilder fileContent = new StringBuilder();
	String lineSeparator = System.getProperty("line.separator");
	static String fileSeparator = File.separator;
	
	String specFileName = null;
	
	public void newFile() {
		fileContent = new StringBuilder();
	}
	
	class ArgsParams {
		String project; 
		String destination; 
		String source; 	
		String path; 
		String protocol; 
	}
	
	public ArgsParams checkParams(String[] args) {
		
		int length = args.length;
		if (length < 4) {
			System.out.println("Usage: ");
			return null;
		}
		
		ArgsParams params = new ArgsParams();
		
		params.project = args[0];		
		params.source = args[1].toUpperCase();
		params.protocol = "http";
		
		if (params.source.equals("-F") || params.source.equals("-H")) {
			params.path = args[2];
		}
				
		if (args[3].equalsIgnoreCase("-o")) {
			params.destination = args[4].concat(fileSeparator).concat(params.project).concat(fileSeparator);
		}		
		
		if (length > 5 ) {
			params.protocol = args[6];
		}
		
		return params;
	}
	
	
	@SuppressWarnings("unchecked")
	public static void main(String[] args) throws Exception {				
		
		if (args.length == 0) {
			System.out.println("Usage: swagger-gentest.jar <DESTINATION FOLDER> <PROTOCOL>");			
		}
		
		Main me = new Main();
		
		ArgsParams AppParams = me.checkParams(args); 
		
		String destFolder = AppParams.destination;
		String specFolder = destFolder.concat("spec").concat(fileSeparator);
				
		String protocol = String.format("%s://", AppParams.protocol);
		String url = null;
		String path = null;
		String method = null;
		JSONObject jMethod = null;
		JSONObject jParam = null;
				
		Map<String, List<String>> mDefinitions = null;
		
		String summary = null;		
		
		JSONArray consumes = null;
		JSONArray produces = null;
		JSONArray parameters = null;
		String paramIn = null,
			   paramName = null;
		
		String defSplit[] = null;
		int limit = 0;
		String sep = ",";
		Object obj = null;
		
				
		boolean hasProduces = false,
				hasConsumes = false,
				hasParameters = false,
				hasBody = false;
		
		List<String> paramsInQuery = new ArrayList<String>(),
					 paramsInPath = new ArrayList<String>(),
					 paramsInFormData = new ArrayList<String>();
		
		
		
		JSONObject jSwagger = null;
		//"http://localhost:8000/v2/api-docs"
		if (AppParams.source.equals("-H")) {
			jSwagger= me.getJsonFromMicroservice(AppParams.path);
		} else {
			me.getJsonFromSwaggerFile(AppParams.path);
		}
		
		JSONObject jInfo = (JSONObject) jSwagger.get("info");
		
		String title = (String) jInfo.get("title");		
		String host = (String) jSwagger.get("host");
				
		Map<String, Object> paths = (Map<String, Object>) jSwagger.get("paths");
		Map<String, Object> methods = null;		
		
		if (jSwagger.containsKey("definitions")) {
			mDefinitions = me.getDefinitions((Map<String, JSONObject>) jSwagger.get("definitions"));
		}		
		
		me.specFileName = title.replaceAll(" ", "").concat("Spec.js");
		me.newFile();
		
		// CODE HEADER 
		me.addOutput("'use strict';\n");
		me.addOutput("var request = require('request'),");
		me.addOutput(4, "chai = require('chai'),");
		me.addOutput(4, "expect = chai.expect;\n");
		me.addOutput("describe(\"%s\", function() {\n", title);
		
		
		// PATH
		for (Map.Entry<String, Object> map : paths.entrySet()) {
			
			path = map.getKey();
			url = protocol.concat(host).concat(path);
			methods = (Map<String, Object>) map.getValue();			
			
			me.addOutput(4, "describe(\"%s\", function() {\n", path);

			
			// METHODS
			for (Map.Entry<String, Object> eMethod: methods.entrySet()) {				
				
				hasProduces = false; 
				hasConsumes = false;
				hasParameters = false;
				hasBody = false;
				
				jMethod = (JSONObject) eMethod.getValue();
				method = eMethod.getKey().toUpperCase();
				summary = (String) jMethod.get("summary");
				
				hasConsumes = jMethod.containsKey("consumes");
				hasProduces =  jMethod.containsKey("produces");
				hasParameters = jMethod.containsKey("parameters");
				
				paramsInFormData.clear();
				paramsInQuery.clear();
				paramsInPath.clear();
				
				if (hasParameters) {
					parameters = (JSONArray) jMethod.get("parameters");
					
					for(Object p: parameters) {						
						jParam = (JSONObject) p;
						
						paramIn = (String) jParam.get("in");
						paramName = (String) jParam.get("name");	
						
						if (paramIn.equals("body")) {
							if (jParam.containsKey("schema")) {
								JSONObject jSchema = ((JSONObject) jParam.get("schema"));
								if (jSchema.containsKey("items")) {
									jSchema = ((JSONObject) jSchema.get("items"));
								}
								paramName = jSchema.get("$ref").toString();
								defSplit = paramName.split("/");
								paramName = defSplit[defSplit.length - 1];
								hasBody = true;
							}
						} else if (paramIn.equals("query")) {
							paramsInQuery.add(paramName);
						} else if (paramIn.equals("path")) {
							paramsInPath.add(paramName);
						} else if (paramIn.equals("formData")) {
							paramsInFormData.add(paramName);							
						}						
					}										
				}
				
				// CODE GENERATION				
				me.addOutput(8, String.format("it(\"Testing %s using %s.\", function(done) {\n", summary, method));
				
				String parameter = null;
				
				// ADD MODEL
				if (hasBody) {
					me.addOutput(12, "var %sModel = {", paramName);
					
					List<String> props = mDefinitions.get(paramName);
					limit = props.size()-1;
					sep = ",";
					
					for(int i = 0; i < props.size(); i++) {
						if (i == limit) sep = "";
						parameter = props.get(i);
						me.addOutput(14, parameter.concat(": '<DATA GOES HERE>'").concat(sep));
						
					}
					
					me.addOutput(12, "};\n");
				}
				
				me.addOutput(12, "request({");
				me.addOutput(16, "url: '%s',", url);
				me.addOutput(16, "method: '%s',", method);
					
				// PARAMETERS
				if (paramsInFormData.size() > 0) {
					me.addOutput(16, "json: {");	
					limit = paramsInFormData.size()-1;
					sep = ",";
					obj = null;	
					for(int i = 0; i < paramsInFormData.size(); i++) {
						if (i == limit) sep = "";
						parameter = paramsInFormData.get(i);
						me.addOutput(18, parameter.concat(": '<DATA GOES HERE>'").concat(sep));
					}						
					me.addOutput(16, "},");						
				}			
				
				if (paramsInQuery.size() > 0) {
					me.addOutput(16, "qs: {");						
					limit = paramsInQuery.size()-1;
					sep = ",";
					obj = null;						
					for(int i = 0; i < paramsInQuery.size(); i++) {
						if (i == limit) sep = "";
						parameter = paramsInQuery.get(i);
						me.addOutput(18, parameter.concat(": '<DATA GOES HERE>'").concat(sep));
					}						
					me.addOutput(16, "},");
				}
				
				// ADD BODY					
				if (hasBody) {
					me.addOutput(16, "json: %sModel,", paramName);
				}
									
				// HEADERS
				if (hasConsumes || hasProduces) {
					me.addOutput(16, "headers: {");
						if (hasConsumes) {
							consumes = (JSONArray) jMethod.get("consumes");
							limit = consumes.size()-1;
							sep = ",";
							obj = null;
							
							for (int i = 0; i < consumes.size(); i++) {
								obj = consumes.get(i);									
								if (!hasProduces && i == limit) 
									sep = "";
								me.addOutput(18, "'Content-Type': '%s'".concat(sep), obj.toString());									
							}								
							/*for(Object consume: consumes) {
								me.addOutput(18, "'Content-Type': '%s',", consume.toString());
							}*/
						}
						
						if (hasProduces) {
							produces = (JSONArray) jMethod.get("produces");
							limit = produces.size()-1;
							sep = ",";
							obj = null;
							
							for (int i = 0; i < produces.size(); i++) {
								obj = produces.get(i);									
								if (i == limit) 
									sep = "";
								me.addOutput(18, "Accept: '%s'".concat(sep), obj.toString());									
							}	
															
						}
													
						me.addOutput(16, "}");
				}				
									
				me.addOutput(14, "},");
				me.addOutput(14, "function(error, res, body) {");
				me.addOutput(16, "expect(res.statusCode).to.equal(200);");
				me.addOutput(16, "done();");
				me.addOutput(14, "});");
			
				me.addOutput(8, "});\n");
				
			}

			me.addOutput(4, "});\n");			
			
		}

		me.addOutput("});");
		
		me.saveSpecFile(specFolder);
		me.createPackageJsonFile(destFolder);
		me.saveJasmineFiles(specFolder);
		
		System.out.println("Your project was generated Successfully in: " + AppParams.destination);
		System.out.println("[1] Run > npm install");
		System.out.println("[2] Run > npm test (to run the testing).");
		
	}
	
	
	public void saveFile(String destination, String fileName, StringBuilder builder) {
		File desFile = new File(destination);
		if (!desFile.exists()) {
			desFile.mkdirs();
		}
		
		File file = new File(destination.concat(fileName));
		if (!file.exists()) {
			try {
				file.createNewFile();
			} catch (IOException e) {			
				e.printStackTrace();
			}
			
		}
		BufferedWriter writer = null;
		try {
		    writer = new BufferedWriter(new FileWriter(file));		
			writer.write(builder.toString());
		 
		} 
		catch (Exception e) {			 
			e.printStackTrace();
		}
		finally {
		    if (writer != null)
				try {
					writer.close();
				} catch (IOException e) {
 					e.printStackTrace();
				}
		}
	}
	
	
	public void saveSpecFile(String destination) {
		
		saveFile(destination, specFileName, fileContent);		
		
	}
	
	public void saveJasmineFiles(String destination) {
		File file = new File(destination.concat(fileSeparator).concat("support"));
		if (!file.exists()) {
			file.mkdirs();
		}
		
		String jasmineFile = destination.concat(fileSeparator).concat("support").concat(fileSeparator);
		
		StringBuilder builder = new StringBuilder();
		builder.append("{" );
		builder.append("  \"spec_dir\": \"spec\",");
		builder.append("  \"spec_files\": [");
		builder.append("\"**/*[sS]pec.js\"");
		builder.append("  ],");
		builder.append("  \"helpers\": [");
		builder.append("\"helpers/**/*.js\"");
		builder.append("  ],");
		builder.append("  \"stopSpecOnExpectationFailure\": false,");
		builder.append("  \"random\": false");
		builder.append("}");
		saveFile(jasmineFile, "jasmine.json", builder);
		
	}
	

	public void createPackageJsonFile(String destination) {
	
		StringBuilder builder = new StringBuilder();		
		builder.append("{\n");
		builder.append("\"name\": \"apiTestingProject\",\n");
		builder.append("\"dependencies\": {\n");
		builder.append("\"express\": \"^4.12.3\",\n");
		builder.append("\"jasmine\": \"^2.5.2\",\n");
		builder.append("\"swagger-express-mw\": \"^0.1.0\"\n");
		builder.append("},\n");
		builder.append("\"devDependencies\": {\n");
		builder.append("\"request\": \"^2.58.0\",\n");
		builder.append("\"chai\": \"^3.0.0\"\n");
		builder.append("},\n");
		builder.append("\"scripts\": {\n");
		builder.append("\"start\": \"node app.js\",\n");
		builder.append("\"test\": \"jasmine\"\n");
		builder.append("}\n");
		builder.append("}\n");
		
		saveFile(destination, "package.json", builder);
		
	}
	
	
	@SuppressWarnings("unchecked")
	private Map<String, List<String>> getDefinitions(Map<String, JSONObject>  definitions) {
		
		String keyDef = null,
			   propName = null;		
		Map<String, JSONObject> model = null,
								properties = null;
		Map<String, List<String>> defMap = new HashMap<String, List<String>>();
		
		// METHODS
		for (Entry<String, JSONObject> eDef: definitions.entrySet()) {			
			keyDef = eDef.getKey();			
			
			if(!keyDef.equalsIgnoreCase("Link")) {
				defMap.put(keyDef, new ArrayList<String>());
								
				model = (Map<String, JSONObject>) eDef.getValue();				
				properties = model.get("properties");
				
				for(Entry<String, JSONObject> eProp: properties.entrySet()) {
					propName = eProp.getKey();
					
					if (!propName.equalsIgnoreCase("links")) {
						
						((List<String>)defMap.get(keyDef)).add(propName);
					}
				}								
			}
			
		}
			
		return defMap;
	}

	public void addOutput(String format, String output) {
		//System.out.println(String.format(format, output));
		fileContent.append(String.format(format, output));
		fileContent.append(lineSeparator);
	}

	public void addOutput(String output) {
		//System.out.println(output);
		fileContent.append(output);
		fileContent.append(lineSeparator);
	}
	
	public void addOutput(int spaces, String output) {
		String spacesstr = "";
		while(spaces-->0){
			spacesstr += " ";
		}
		//System.out.println(spacesstr + output);
		fileContent.append(spacesstr.concat(output));
		fileContent.append(lineSeparator);
	}
	
	
	public void addOutput(int spaces, String format, String output) {
		String spacesstr = "";
		while(spaces-->0){
			spacesstr += " ";
		}
		//System.out.println(spacesstr + String.format(format, output));
		fileContent.append(spacesstr.concat(String.format(format, output)));
		fileContent.append(lineSeparator);
	}
	
	
	public JSONObject getJsonFromMicroservice(String url) {
		
		JSONParser jsonParser = new JSONParser();
		JSONObject jsonObject = null;		
		StringBuilder data = new StringBuilder();
		
		try {
			URL mUrl = new URL(url);
			URLConnection yc = mUrl.openConnection();
			BufferedReader in = new BufferedReader(new InputStreamReader(
	                                yc.getInputStream()));
			String inputLine;
			while ((inputLine = in.readLine()) != null) { 
				data.append(inputLine);
			}
	       
			in.close();
			jsonObject = (JSONObject)jsonParser.parse(data.toString());	        
		}
		catch(Exception ex) {
			ex.printStackTrace();
		}
		return jsonObject;
	}
	
	public JSONObject getJsonFromSwaggerFile(String filename) throws Exception {
		ClassLoader classLoader = this.getClass().getClassLoader();
		File file = new File(classLoader.getResource(filename).getFile());
		FileReader reader = new FileReader(file);
		JSONParser jsonParser = new JSONParser();
		JSONObject jsonObject = null;
		try {
			jsonObject = (JSONObject) jsonParser.parse(reader);
			
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		return jsonObject;

	}
	

}
