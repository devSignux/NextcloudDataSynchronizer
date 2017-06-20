package space.signux;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedList;
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

	private Sardine input;
	private Sardine output;

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

		input = SardineFactory.begin(inputConnection.getUsername(), inputConnection.getPassword());
		output = SardineFactory.begin(outputConnection.getUsername(), outputConnection.getPassword());

		String startInputFolder = inputConnection.getUserFolder();
		String startOutputFolder = outputConnection.getUserFolder();

		try {
			log.info("read input resources");
			List<DavResource> resourcesInput = getResourcesList(input, inputConnection, startInputFolder);
			log.info("read output resources");
			List<DavResource> resourcesOutput = getResourcesList(output, outputConnection, startOutputFolder);
			log.info("read finished");

			archiveResources(resourcesInput, resourcesOutput);

		} catch (IOException e) {
			log.error(e.getMessage(), e);
		}

		log.debug("synchronizeData() finished");
		return true;
	}

	private void archiveResources(List<DavResource> resourcesInput, List<DavResource> resourcesOutput) {
		List<DavResource> notExistendResources = new LinkedList<DavResource>();
		List<DavResource> contentLengthDiffersResources = new LinkedList<DavResource>();

		for (DavResource inputResource : resourcesInput) {
			DavResource removeResource = null;
			boolean notExist = true;
			for (DavResource outputResource : resourcesOutput) {
				if (inputResource.getPath().equals(outputResource.getPath())) {
					log.debug(inputResource.getPath() + " exist on output");
					if (!inputResource.isDirectory()
							&& inputResource.getModified().after(outputResource.getModified())) {
						log.debug(inputResource.getPath() + " has on input a newer modifierd date");
						if (!inputResource.getContentLength().equals(outputResource.getContentLength())) {
							log.debug(inputResource.getPath() + " has a different content length: "
									+ inputResource.getContentLength() + " <-> " + outputResource.getContentLength());
							contentLengthDiffersResources.add(inputResource);
						}
						removeResource = outputResource;
					}
					notExist = false;
					break;
				}
			}
			if (removeResource != null) {
				resourcesOutput.remove(removeResource);
			}
			if (notExist) {
				notExistendResources.add(inputResource);
			}
		}
		addNewResources(notExistendResources);
		updateResources(contentLengthDiffersResources);
	}

	private void updateResources(List<DavResource> updateResources) {
		log.debug("update " + updateResources.size() + " resources");
		for (DavResource resource : updateResources) {
			updateResource(resource);
		}
		log.debug("update resources finished");
	}

	private void addNewResources(List<DavResource> newResources) {
		log.debug("create " + newResources.size() + " new resources");
		while (newResources.size() > 0) {
			List<DavResource> createdFolders = new LinkedList<DavResource>();
			// first add new folders
			for (DavResource newResource : newResources) {
				if (newResource.isDirectory()) {
					String putUrl = createEncodedUrl(outputConnection, newResource.getPath());
					log.info("create output directory: " + putUrl);
					try {
						output.createDirectory(putUrl);
						createdFolders.add(newResource);
					} catch (IOException e) {
						log.warn(newResource.getPath() + " can't create folder...");
					}
				}
			}
			newResources.removeAll(createdFolders);
			List<DavResource> createdFiles = new LinkedList<DavResource>();
			for (DavResource newResource : newResources) {
				if (newResource.isDirectory()) {
					break;
				}
				if (updateResource(newResource)) {
					createdFiles.add(newResource);
				}
			}
			newResources.removeAll(createdFiles);
		}
		log.debug("new created resources finished");
	}

	private boolean updateResource(DavResource newResource) {
		String getUrl = createEncodedUrl(inputConnection, newResource.getPath());

		try {
			InputStream inputStream = input.get(getUrl);
			// todo: check if free space is available
			log.info("write input resource: " + newResource.getPath() + " into temporarly file");
			File tmpInputFile = writeStreamIntoFile(newResource, inputStream);

			String putUrl = createEncodedUrl(outputConnection, newResource.getPath());
			log.info("write temporarly file into output resource: " + putUrl);
			output.put(putUrl, tmpInputFile, newResource.getContentType());

			log.debug("delete temporarly file: " + tmpInputFile.getPath());
			tmpInputFile.delete();
			return true;
		} catch (FileNotFoundException ex) {
			log.error("can't create tmeporarly file for input resource: " + newResource.getPath(), ex);
		} catch (IOException ex) {
			log.error("error while reading input stream or writing data into temporarly file", ex);
		}
		return false;
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
		
		log.debug("free tmp space: " + tmpInputFile.getFreeSpace());
		if (tmpInputFile.getFreeSpace() < inputResources.getContentLength()) {
			log.warn("not enough space ( " + tmpInputFile.getFreeSpace() + " ) for: " + inputResources.getPath()
					+ " size: " + inputResources.getContentLength());
			throw new IOException("Not enough space");
		}
		
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

	private List<DavResource> getResourcesList(Sardine sardine, ConnectionSettings settings, String folder)
			throws IOException {
		log.debug("start getResourcesList()");

		String urlInput = createEncodedUrl(settings, folder);
		List<DavResource> resources = sardine.list(urlInput, -1);

		log.debug("getResourcesList() finished");
		return resources;
	}

	// this it currently a bad way to encodeUrl, refactoring needed
	private static String encodeUrl(String url) {
		return url.replace("%", "%25").replace(" ", "%20").replace("{", "%7B").replace("}", "%7D").replace("[", "%5B")
				.replace("]", "%5D").replace("#", "%23").replace("`", "%60");
	}
}
