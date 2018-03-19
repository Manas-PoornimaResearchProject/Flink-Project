package it.polimi.dice.dicer;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Scanner;

import tosca.*;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.uml2.uml.UMLPackage;

import it.polimi.dice.dicer.ToscaDslStandaloneSetup;
import org.yaml.snakeyaml.Yaml;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Injector;
import com.ibm.icu.util.BytesTrie.Iterator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import es.unizar.disco.dice.*;

public class Dicer {
    private static final Logger logger = LoggerFactory.getLogger(Dicer.class);

    public final static String DDSM_METAMODEL_NAME = "DDSM";
    public final static String UML_METAMODEL_NAME = "MMUML";
    public final static String OUT_METAMODEL_NAME = "TOSCA";
    public final static String TRANSFORMATION_MODULE = "ddsm2tosca";
    public final static String UML_TRANSFORMATION_MODULE = "uml2tosca";

    @Parameter(names = { "-h", "--help", "-help" }, help = true, description = "Shows this help")
    private boolean help = false;

    @Parameter(names = "-inModel", description = "The path to the input DDSM model.")
    public String inModelPath = "./models/spark.uml";

    @Parameter(names = "-outModel", description = "The path for the output TOSCA model.")
    public String outModelPath = "./models/spark_out";
    
    //------------------- FLINK -----------------------
    @Parameter(names = "-inFlinkModel", description = "The path to the input DDSM model.")
    public String inFlinkModelPath = "./models/flink.uml";

    @Parameter(names = "-outFlinkModel", description = "The path for the output TOSCA model.")
    public String outFlinkModelPath = "./models/flink_out";
    //------------------- FLINK -----------------------
    
    @Parameter(names = "-inMetamodel", description = "The path to the DDSM metamodel.")
    public String ddsmMetamodelPath = "./metamodels/ddsm.ecore";

    @Parameter(names = "-outMetamodel", description = "The path for the TOSCA metamodel.")
    public String outMetamodelPath = "./metamodels/tosca.ecore";

    @Parameter(names = "-umlMetamodel", description = "The path for the UML metamodel.")
    public String umlMetamodelPath = "./metamodels/DICE.profile.uml";

    @Parameter(names = "-secureUmlMetamodel", description = "The path for the SecureUML metamodel.")
    public String secureUmlProfilePath = "./metamodels/SecureUML.profile.uml";

    @Parameter(names = "-transformationDir", description = "The path for the directory containing the ddsm2tosca ATL transformation.")
    public String transformationDir = "./transformations/";

    @Parameter(names = "-runInTextMode", description = "Tells if to run just the model to text transformation or not.\n"
            + "If true, the -outModel option have to point to an actual .xmi model conforming to the tosca.ecore metamodel."
            + "The -inModel and the -transformationDir options will be instead ignored.")
    public int runInTextMode = 0;

    public static void main(String[] args) throws IOException {
        Dicer dicer = new Dicer();
        JCommander jc = new JCommander(dicer, args);

        if (dicer.help) {
            jc.usage();
            System.exit(0);
        } else {
            logger.info("Running DICER on input model: " + dicer.inModelPath);
            dicer.runme(dicer.inModelPath, dicer.outModelPath, dicer.ddsmMetamodelPath, dicer.umlMetamodelPath,
                    dicer.secureUmlProfilePath, dicer.outMetamodelPath, dicer.transformationDir, dicer.runInTextMode);
          //------------------- FLINK -----------------------
            logger.info("Running DICER on input model: " + dicer.inFlinkModelPath);
            dicer.runme(dicer.inFlinkModelPath, dicer.outFlinkModelPath, dicer.ddsmMetamodelPath, dicer.umlMetamodelPath,
                    dicer.secureUmlProfilePath, dicer.outMetamodelPath, dicer.transformationDir, dicer.runInTextMode);
          //------------------- FLINK -----------------------
        }
    }

    @SuppressWarnings("unchecked")
    public void runme(String inputXMIModelPath, String outModelPath, String ddsmMetamodelPath, String umlMetamodelPath,
            String secureUmlProfilePath, String outMetamodelPath, String transformationDir, int runInTextMode) {

        if (runInTextMode == 0) {
            ATLTransformationLauncher l = new ATLTransformationLauncher();
            logger.info("Running the ATL transformation.");

            logger.info("Registering the output TOSCA metamodel.");
            l.registerOutputMetamodel(outMetamodelPath);

            File file = new File(inputXMIModelPath);
            boolean isUml = false;

            try {
                Scanner scanner = new Scanner(file);

                while (scanner.hasNextLine()) {
                    String line = scanner.nextLine();
                    if (line.toLowerCase().contains("uml")) {
                        isUml = true;
                    }
                }
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }

            if (isUml) {
                logger.info("Registering the input UML metamodel.");
                l.setInputMetamodelNsURI(UMLPackage.eINSTANCE.getNsURI());
                l.setDiceProfilePath(umlMetamodelPath);
                l.setSecureUMLProfilePath(secureUmlProfilePath);
                l.launch(true, inputXMIModelPath, UML_METAMODEL_NAME, outModelPath + ".xmi", OUT_METAMODEL_NAME,
                        transformationDir, UML_TRANSFORMATION_MODULE);
            } else {
                logger.info("Registering the input DDSM metamodel.");
                l.registerInputMetamodel(ddsmMetamodelPath);
                l.launch(false, inputXMIModelPath, DDSM_METAMODEL_NAME, outModelPath + ".xmi", OUT_METAMODEL_NAME,
                        transformationDir, TRANSFORMATION_MODULE);
            }
        }

        logger.info("Running the Xtext grammar to serialize the output TOSCA model.");
        EPackage.Registry.INSTANCE.put("http://tosca/1.0", ToscaPackage.eINSTANCE);

        Injector injector = new ToscaDslStandaloneSetup().createInjectorAndDoEMFRegistration();
        ResourceSet xmiResourceSet = injector.getInstance(ResourceSet.class);

        Resource xmi_resource = (Resource) xmiResourceSet.getResource(URI.createURI(outModelPath + ".xmi"), true);

        ResourceSet xtext_resourceSet = injector.getInstance(ResourceSet.class);
        Resource textualModel_resource = (Resource) xtext_resourceSet
                .createResource(URI.createURI(outModelPath + ".json"));

        textualModel_resource.getContents().add(xmi_resource.getContents().get(0));

        logger.info("Saving the output JSON model.");
        try {
            textualModel_resource.save(null);
        } catch (IOException e) {
            e.printStackTrace();
        }

        try {
            logger.info("Generating TOSCA YAML blueprint from output JSON model.");
            String actualObj = readFile(outModelPath + ".json", Charset.defaultCharset());
            ObjectMapper mapper = new ObjectMapper();
            JsonNode node = mapper.readTree(actualObj);
            Yaml yaml = new Yaml();
            Map<String, Object> map = (Map<String, Object>) yaml.load(node.toString());

            Map<String, Object> nodeTemplates = (Map<String, Object>) map.get("node_templates");
            Map<String, Object> tmpTemplates = new HashMap<String, Object>();

            if(nodeTemplates != null) {
                for (java.util.Iterator<Entry<String, Object>> it = nodeTemplates.entrySet().iterator(); it.hasNext();) {
                    Map<String, Object> nodeTemplate = (Map<String, Object>) it.next().getValue();
                    for (java.util.Iterator<Entry<String, Object>> it2 = nodeTemplate.entrySet().iterator(); it2
                            .hasNext();) {
                        Entry<String, Object> attribute = it2.next();
                        if (attribute.getKey().equals("type")) {
                            String type = (String) attribute.getValue();
                            if (type.contains("docker.Server") || type.contains("osv")) {
                                Map<String, Object> fw_ephemeral = new HashMap<String, Object>();
                                fw_ephemeral.put("type", "dice.firewall_rules.Base");
                                Map<String, Object> rules = new HashMap<String, Object>();
                                rules.put("ip_prefix", "0.0.0.0/0");
                                rules.put("protocol", "tcp");
                                rules.put("from_port", 32768);
                                rules.put("to_port", 61000);
                                Map<String, Object> properties = new HashMap<String, Object>();
                                properties.put("rules", new ArrayList<Map<String,Object>>() {{ add(rules); }});
                                fw_ephemeral.put("properties", properties);

                                tmpTemplates.put("fw_ephemeral", fw_ephemeral);
                            }
                            
                            if (type.contains("dice.hosts") || type.contains("User") || type.contains("osv")) {
                                Map<String, Object> properties = (Map<String, Object>) nodeTemplate.get("properties");
                                for (java.util.Iterator<Entry<String, Object>> it3 = properties.entrySet().iterator(); it3
                                        .hasNext();) {
                                    if (it3.next().getKey().equals("monitoring")) {
                                        it3.remove();
                                    }
                                }
                            }
                            
                            if(type.equals("dice.components.spark.Topology")){
                                type = "dice.components.spark.Application";
                            	Map<String, Object> properties = (Map<String, Object>) nodeTemplate.get("properties");
                                Object arguments = null;
                                String application = "";
                                String topology_class = "";
                                String topology_name = "";
                                for (Map.Entry<String, Object> p : properties.entrySet()) {
                                    if(p.getKey().equals("arguments")) {
                                        arguments = p.getValue();
                                    }
                                    if(p.getKey().equals("application")) {
                                        application = (String) p.getValue();
                                    }
                                    if(p.getKey().equals("topology_class")) {
                                        topology_class = (String) p.getValue();
                                    }
                                    if(p.getKey().equals("topology_name")) {
                                        topology_name = (String) p.getValue();
                                    }
                                }
                                for (java.util.Iterator<Entry<String, Object>> it3 = properties.entrySet().iterator(); it3
                                        .hasNext();) {
                                    String tmpProp = it3.next().getKey();
                                    if (tmpProp.equals("arguments")
                                            | tmpProp.equals("application")
                                            | tmpProp.equals("topology_class")
                                            | tmpProp.equals("topology_name")) {
                                        it3.remove();
                                    }
                                }
                                
                                properties.put("args", arguments);
                                properties.put("jar", application);
                                properties.put("class", topology_class); 
                                properties.put("name", topology_name);

                            }
                          //------------------- FLINK -----------------------
                            if(type.equals("dice.components.flink.Topology")) {
                                type = "dice.components.flink.Application";
                            	Map<String, Object> properties = (Map<String, Object>) nodeTemplate.get("properties");
                                Object arguments = null;
                                String application = "";
                                String topology_class = "";
                                String topology_name = "";
                                for (Map.Entry<String, Object> p : properties.entrySet()) {
                                    if(p.getKey().equals("arguments")) {
                                        arguments = p.getValue();
                                    }
                                    if(p.getKey().equals("application")) {
                                        application = (String) p.getValue();
                                    }
                                    if(p.getKey().equals("topology_class")) {
                                        topology_class = (String) p.getValue();
                                    }
                                    if(p.getKey().equals("topology_name")) {
                                        topology_name = (String) p.getValue();
                                    }
                                }
                                for (java.util.Iterator<Entry<String, Object>> it3 = properties.entrySet().iterator(); it3
                                        .hasNext();) {
                                    String tmpProp = it3.next().getKey();
                                    if (tmpProp.equals("arguments")
                                            | tmpProp.equals("application")
                                            | tmpProp.equals("topology_class")
                                            | tmpProp.equals("topology_name")) {
                                        it3.remove();
                                    }
                                }
                                
                                properties.put("args", arguments);
                                properties.put("jar", application);
                                properties.put("class", topology_class);
                                properties.put("name", topology_name);

                            }
                          //------------------- FLINK -----------------------
                        }
                    }
                }
                
                for (java.util.Iterator<Entry<String, Object>> it = tmpTemplates.entrySet().iterator(); it.hasNext();) {
                    Entry<String, Object> n= it.next();
                    nodeTemplates.put(n.getKey(), n.getValue());
                }
                
                
            }

            try (PrintWriter out = new PrintWriter(outModelPath + ".yaml")) {
                out.println(yaml.dump(map));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        logger.info("Completed.");
    }

    static String readFile(String path, Charset encoding) throws IOException {
        byte[] encoded = Files.readAllBytes(Paths.get(path));
        return new String(encoded, encoding);
    }
}
