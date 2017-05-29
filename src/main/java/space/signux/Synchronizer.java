package space.signux;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;
import java.util.Properties;

import org.apache.log4j.Logger;
import org.apache.log4j.xml.DOMConfigurator;

import com.github.sardine.DavResource;
import com.github.sardine.Sardine;
import com.github.sardine.SardineFactory;

public class Synchronizer {

	private ConnectionSettings inputConnection;
	private ConnectionSettings outputConnection;

	private static String tmpDirPath = "ncds";
	private static Logger log = Logger.getLogger(Synchronizer.class);

	private File tempDir;

	public Synchronizer(String[] args) {
		log.debug("create Synchronizer(args: " + args + ")");

		if (!loadSettings(args)) {
			return;
		}
		createTempFolder();

		log.debug("Synchronizer() created");
	}

	public static void main(String[] args) {
		log.debug("start main()");

		DOMConfigurator.configureAndWatch( "log4j-4.xml", 60*1000 );

		Synchronizer synchronizer = new Synchronizer(args);
		synchronizer.synchronizeData();

		log.debug("main() finished");
	}

	private boolean loadSettings(String[] args) {
		log.info("start loadSettings(args: " + args + ")");

		String filename = "settings.txt";
		if (args.length > 0) {
			filename = args[0];
		}
		File settingsFile = new File(filename);

		if (!settingsFile.exists()) {
			log.error("settings file: " + filename + " doesn't exist!");
			return false;
		}

		FileInputStream in;
		try {
			log.info("load settings from file: " + filename);

			in = new FileInputStream(settingsFile);
			Properties properties = new Properties();
			properties.load(in);

			inputConnection = SettingsReader.CreateInputConnectionSettings(properties);
			outputConnection = SettingsReader.CreateOutputConnectionSettings(properties);
		} catch (FileNotFoundException e) {
			log.error("Can't find file: " + filename);
			return false;
		} catch (IOException e) {
			log.error("Can't load file: " + filename);
			return false;
		}
		log.debug("loadSettings() finished");
		return true;
	}

	private void createTempFolder() {
		log.debug("start createTempFolder()");

		String path = System.getProperty("java.io.tmpdir");
		tempDir = new File(path + "/" + tmpDirPath);
		if (!tempDir.exists()) {
			log.info("create temp folder: " + tempDir.getAbsolutePath());
			tempDir.mkdirs();
		}

		log.debug("delete temp folder: " + tempDir.getAbsolutePath() + " on exit");
		tempDir.deleteOnExit();

		log.debug("createTempFolder(); finished");
	}

	private boolean synchronizeData() {

		log.debug("start synchronizeData()");

		Sardine input = SardineFactory.begin(inputConnection.getUsername(), inputConnection.getPassword());
		Sardine output = SardineFactory.begin(outputConnection.getUsername(), outputConnection.getPassword());

		String startInputFolder = inputConnection.getUserFolder();
		String startOutputFolder = outputConnection.getUserFolder();

		log.info("start synchronizing");
		
		synchronizeResources(input, output, startInputFolder, startOutputFolder);
		
		log.info("synchronizing finished");

		log.debug("synchronizeData() finished");
		return true;
	}

	private void synchronizeResources(Sardine input, Sardine output, String startInputFolder,
			String startOutputFolder) {
		log.debug("start synchronizeResources(startInputFolder: " + startInputFolder + " )");

		List<DavResource> resourcesOutput;
		List<DavResource> resourcesInput;

		log.debug("synchronizeResources(startInputFolder: " + startInputFolder + " ) finished");
	}

}
