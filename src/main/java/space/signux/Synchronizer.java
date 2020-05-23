package space.signux;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.IDN;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.apache.log4j.Logger;
import org.apache.log4j.xml.DOMConfigurator;

import com.github.sardine.DavResource;
import com.github.sardine.Sardine;
import com.github.sardine.SardineFactory;

import space.signux.helper.Blacklist;

public class Synchronizer {

	private static Logger log = Logger.getLogger(Synchronizer.class);

	private Sardine input;
	private ConnectionSettings inputConnection;

	private Sardine output;
	private ConnectionSettings outputConnection;

	private Integer deleteFilesOlderThanDays;

	private Set<String> deleteBlacklist;

	public Synchronizer(String[] args) {
		log.debug("create Synchronizer(args: " + Arrays.deepToString(args) + ")");

		if (!loadSettings(args)) {
			return;
		}

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
		log.info("start loadSettings(args: " + Arrays.deepToString(args) + ")");

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

			// read property deleteFilesOlderThanDays
			try {
				deleteFilesOlderThanDays = Integer.parseInt(properties.getProperty("deleteFilesOlderThanDays", null));
			} catch (ClassCastException | NumberFormatException e) {
				log.warn("Can't parse value for property deleteFilesOlderThanDays");
			}
			log.info("use deleteFilesOlderThanDays: " + deleteFilesOlderThanDays);

			// read property deleteBlacklist
			try {
				String deleteBlacklistFile = properties.getProperty("deleteBlacklist", null);
				deleteBlacklist = Blacklist.getDeleteBlacklistFromFile(deleteBlacklistFile);
			} catch (ClassCastException e) {
				log.warn("Can't parse value for property deleteBlacklist");
			}
			log.info("use deleteBlacklist: " + deleteBlacklist);
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

	private void synchronizeData() {
		log.info("start synchronizeData()");
		log.info("Input(" + inputConnection.getUsername() + "): " + inputConnection.getBaseUrl());
		log.info("Output(" + outputConnection.getUsername() + "): " + outputConnection.getBaseUrl());

		input = SardineFactory.begin(inputConnection.getUsername(), inputConnection.getPassword());
		output = SardineFactory.begin(outputConnection.getUsername(), outputConnection.getPassword());

		listSubFolder(inputConnection.getBaseFolder());

		log.info("end synchronizeData()");
	}

	private void listSubFolder(String path) {
		log.debug("check path: " + path);
		try {
			String cloudURL = getEncodedUrl(path, inputConnection);
			String archivURL = getEncodedUrl(path, outputConnection);
			try {
				List<DavResource> cloudResources = input.list(cloudURL, 1);
				List<DavResource> archivResources = output.list(archivURL, 1);
				for (DavResource cloudResource : cloudResources) {
					if (cloudResource.getPath().equals(path)) {
						continue;
					}

					boolean directoryCreated = createResourceInArchiv(cloudResource, archivResources);

					if (cloudResource.isDirectory()
							&& (directoryCreated || isCloudDirectoryNewer(cloudResource, archivResources))) {
						listSubFolder(cloudResource.getPath());
					}

					long unmodifiedDays = UnmodifiedSinceDays(cloudResource);
					if (deleteFilesOlderThanDays != null && unmodifiedDays > deleteFilesOlderThanDays /*
																										 * 3Jahre (1092)
																										 */
							&& Blacklist.deletePath(cloudResource.getPath(), deleteBlacklist)) {
						log.info("Delete '" + cloudResource.getPath() + "' unmodified since " + unmodifiedDays
								+ " days");
						if (existInArchiv(cloudResource, archivResources)) {
							input.delete(getEncodedUrl(cloudResource.getPath(), inputConnection));
						}
					}
				}

				cloudResources = input.list(cloudURL, 1);
				// remove directory if empty
				if (deleteFilesOlderThanDays != null && cloudResources.size() < 2) {
					log.info("Delete empty folder '" + path + "'");
					input.delete(cloudURL);
				}

			} catch (IOException e) {
				log.error(e.getMessage(), e);
			}
		} catch (MalformedURLException | URISyntaxException e) {
			log.error(e.getMessage(), e);
		}
	}

	private boolean existInArchiv(DavResource cloudResource, List<DavResource> archivResources) {
		for (DavResource archivResource : archivResources) {
			if (cloudResource.getPath().equals(archivResource.getPath())
					&& cloudResource.getContentLength().equals(archivResource.getContentLength())
					&& cloudResource.getContentType().equals(archivResource.getContentType())) {
				return true;
			}
		}
		return false;
	}

	private boolean isCloudDirectoryNewer(DavResource cloudResource, List<DavResource> archivResources) {
		// check sub folder if fastMode is diabled
		if (deleteFilesOlderThanDays != null) {
			return true;
		}
		for (DavResource archivResource : archivResources) {
			if (cloudResource.getPath().equals(archivResource.getPath()) && archivResource.isDirectory()) {
				if (archivResource.getModified().before(cloudResource.getModified())) {
					long diff = TimeUnit.HOURS.convert(
							Math.abs(archivResource.getModified().getTime() - cloudResource.getModified().getTime()),
							TimeUnit.MILLISECONDS);
					log.debug("Time diff: " + diff + " hours");
					return true;
				}
				break;
			}
		}
		return false;
	}

	private boolean createResourceInArchiv(DavResource cloudResource, List<DavResource> archivResources)
			throws IOException, URISyntaxException {
		boolean existInArchiv = false;
		Date archivModifiedDate = null;
		for (DavResource archivResource : archivResources) {
			if (cloudResource.getPath().equals(archivResource.getPath())
					&& cloudResource.getContentType().equals(archivResource.getContentType())) {
				archivModifiedDate = archivResource.getModified();
				existInArchiv = true;
				break;
			}
		}
		if (existInArchiv) {
			if (!cloudResource.isDirectory() && archivModifiedDate != null
					&& archivModifiedDate.before(cloudResource.getModified())) {
				log.info("Update in archiv the modified file: '" + cloudResource.getPath() + "'");
				String inputUrl = getEncodedUrl(cloudResource.getPath(), inputConnection);
				String outputUrl = getEncodedUrl(cloudResource.getPath(), outputConnection);
				output.put(outputUrl, input.get(inputUrl), cloudResource.getContentType());
			}
		} else {
			if (cloudResource.isDirectory()) {
				log.info("Create in archiv the new directory: '" + cloudResource.getPath() + "'");
				output.createDirectory(cloudResource.getPath());
				return true;
			} else {
				log.info("Create in archiv the new file: '" + cloudResource.getPath() + "'");
				String inputUrl = getEncodedUrl(cloudResource.getPath(), inputConnection);
				String outputUrl = getEncodedUrl(cloudResource.getPath(), outputConnection);
				output.put(outputUrl, input.get(inputUrl), cloudResource.getContentType());
			}
		}
		return false;
	}

	private long UnmodifiedSinceDays(DavResource resource) {
		long unmodifiedDays = TimeUnit.DAYS.convert(Math.abs(new Date().getTime() - resource.getModified().getTime()),
				TimeUnit.MILLISECONDS);
		return unmodifiedDays;
	}

	private String getEncodedUrl(String path, ConnectionSettings connection)
			throws MalformedURLException, URISyntaxException {
		URL url = new URL(connection.getBaseUrl() + path);
		URI uri = new URI(url.getProtocol(), url.getUserInfo(), IDN.toASCII(url.getHost()), url.getPort(),
				url.getPath(), url.getQuery(), url.getRef());
		String correctEncodedURL = uri.toASCIIString();
		correctEncodedURL = correctEncodedURL.replace("#", "%23");
		return correctEncodedURL;
	}
}
