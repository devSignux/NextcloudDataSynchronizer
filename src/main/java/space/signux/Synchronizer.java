package space.signux;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
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

		DOMConfigurator.configureAndWatch("log4j-4.xml", 60 * 1000);

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

		try {
			List<DavResource> resourcesInput = getResourcesList(input, inputConnection, startInputFolder);
			List<DavResource> resourcesOutput = getResourcesList(output, outputConnection, startOutputFolder);

			if (resourcesInput.size() != resourcesOutput.size()) {
				log.info("different folder size.. input: " + resourcesInput.size() + " output: "
						+ resourcesOutput.size());
			}

			for (int i = 0; i < resourcesInput.size();) {

				DavResource inputResources = resourcesInput.get(i);
				DavResource outputResources = getExistingResource(resourcesOutput, inputResources);

				if (outputResources == null) {
					log.info("output path doesn't exist: " + inputResources.getPath() + " size: "
							+ inputResources.getContentLength());

					String putUrl = createEncodedUrl(outputConnection, inputResources.getPath());

					log.debug("putUrl: " + putUrl);

					// check if input resource is a directory, than create a new
					// directory
					if (inputResources.getContentLength() == -1) {
						log.info("create output directory: " + putUrl);
						output.createDirectory(putUrl);
						resourcesOutput = getResourcesList(output, outputConnection, startOutputFolder);
						continue;
					} else {
						String getUrl = createEncodedUrl(inputConnection, inputResources.getPath());
						InputStream inputStream = input.get(getUrl);

						try {
							log.info("write input resource: " + inputResources.getPath() + " into temporarly file");
							File tmpInputFile = writeStreamIntoFile(inputResources, inputStream);

							log.info("write temporarly file into output resource: " + putUrl);
							output.put(putUrl, tmpInputFile, inputResources.getContentType());

							log.debug("delete temporarly file: " + tmpInputFile.getPath());
							tmpInputFile.delete();
						} catch (FileNotFoundException ex) {
							log.error("can't create tmeporarly file for input resource: " + inputResources.getPath(),
									ex);
						} catch (IOException ex) {
							log.error("error while reading input stream or writing data into temporarly file", ex);
						}
					}
				}

				// check if size of file is different
				if (outputResources != null && inputResources.getContentLength() != inputResources.getContentLength()) {
					log.warn("ContentLength is different input resource: " + inputResources.getPath() + "("
							+ inputResources.getContentLength() + ") output resource: " + outputResources.getPath()
							+ "(" + outputResources.getContentLength() + ")");
				}
				// if input and output resource are folders and both exist, than
				// start with the internal content
				if (inputResources.getContentLength() == -1 && outputResources != null
						&& !inputResources.getPath().equals(startInputFolder)
						&& inputResources.getEtag() != outputResources.getEtag()) {
					synchronizeResources(input, output, inputResources.getPath(), outputResources.getPath());
				}
				// go to the next input resource if we have the current
				// file/folder completed
				i++;
			}

		} catch (IOException e) {
			log.error(e.getMessage(), e);
		}

		log.debug("synchronizeResources(startInputFolder: " + startInputFolder + " ) finished");
	}

	private String createEncodedUrl(ConnectionSettings settings, String folderPath) {
		log.debug("start createEncodedUrl()");
		String url = settings.getFullFolderUri(folderPath);
		url = encodeUrl(url);
		log.debug("createEncodedUrl() finished with url: " + url);
		return url;
	}

	private File writeStreamIntoFile(DavResource inputResources, InputStream inputStream)
			throws FileNotFoundException, IOException {
		log.debug("start writeStreamIntoFile()");

		File tmpInputFile = new File(tempDir.getAbsolutePath() + File.pathSeparator + inputResources.getName());
		FileOutputStream tmpFileOutputStream = new FileOutputStream(tmpInputFile);

		int nRead;
		byte[] data = new byte[16384];

		while ((nRead = inputStream.read(data, 0, data.length)) != -1) {
			tmpFileOutputStream.write(data, 0, nRead);
		}

		tmpFileOutputStream.flush();
		tmpFileOutputStream.close();
		inputStream.close();

		log.debug("writeStreamIntoFile() finished");

		return tmpInputFile;
	}

	private DavResource getExistingResource(List<DavResource> resourcesOutput, DavResource inputResources) {
		DavResource outputResources = null;
		for (DavResource res : resourcesOutput) {
			if (res.getName().equals(inputResources.getName())
					&& res.getPath().length() == (inputResources.getPath().length())) {
				outputResources = res;
				break;
			}
		}
		return outputResources;
	}

	private List<DavResource> getResourcesList(Sardine sardine, ConnectionSettings settings, String folder)
			throws IOException {
		log.debug("start getResourcesList()");

		String urlInput = createEncodedUrl(settings, folder);
		List<DavResource> resources = sardine.list(urlInput);

		log.debug("getResourcesList() finished");
		return resources;
	}

	// this it currently a bad way to encodeUrl, refactoring needed
	private static String encodeUrl(String url) {
		return url.replace("%", "%25").replace(" ", "%20").replace("{", "%7B").replace("}", "%7D").replace("[", "%5B")
				.replace("]", "%5D").replace("#", "%23").replace("`", "%60");
	}
}
