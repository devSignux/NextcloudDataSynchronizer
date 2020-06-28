package space.signux;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import javax.naming.directory.InvalidAttributeValueException;

import org.apache.commons.io.FileSystemUtils;
import org.apache.log4j.Logger;
import org.apache.log4j.xml.DOMConfigurator;

import com.github.sardine.DavResource;
import com.github.sardine.Sardine;
import com.github.sardine.SardineFactory;
import com.google.common.io.Files;

public class IPhoneImageConverter {

	private static Logger log = Logger.getLogger(IPhoneImageConverter.class);

	private Sardine input;
	private ConnectionSettings inputConnection;

	private File tempDir;

	private String ffmpegPath;
	private String imageMagickConvertPath;
	private String originalIPhoneDataPath; // ="Photos/IPhone";
	private String outputPhotosDataPath; // = "Photos";

	public static void main(String[] args) throws Exception {
		DOMConfigurator.configureAndWatch("log4j-4.xml", 60 * 1000);

		IPhoneImageConverter converter = new IPhoneImageConverter(args);
		converter.convertIphoneImages();
	}

	public IPhoneImageConverter(String[] args) throws InvalidAttributeValueException, IOException {
		log.debug("create Synchronizer(args: " + Arrays.deepToString(args) + ")");

		loadSettings(args);
		createTempFolder();

		log.debug("Synchronizer() created");
	}

	private void loadSettings(String[] args) throws InvalidAttributeValueException, IOException {
		log.info("start loadSettings(args: " + Arrays.deepToString(args) + ")");

		String filename = "settings.txt";
		if (args.length > 0) {
			filename = args[0];
		}
		File settingsFile = new File(filename);

		if (!settingsFile.exists()) {
			log.error("settings file: " + filename + " doesn't exist!");
			throw new IOException("settings file: " + filename + " doesn't exist!");
		}

		FileInputStream in;
		try {
			log.info("load settings from file: " + filename);

			in = new FileInputStream(settingsFile);
			Properties properties = new Properties();
			properties.load(in);
			Encryptor encryptor = new Encryptor();
			encryptor.encryptPasswords(properties);
			properties.store(new FileOutputStream(settingsFile), null);

			inputConnection = SettingsReader.CreateInputConnectionSettings(properties, encryptor);
			log.info("Input(" + inputConnection.getUsername() + "): " + inputConnection.getBaseUrl());

			// read property ffmpegPath
			ffmpegPath = properties.getProperty("ffmpegPath", null);
			if (ffmpegPath == null) {
				throw new InvalidAttributeValueException("ffmpegPath parameter is null!");
			}
			log.info("use ffmpegPath: " + ffmpegPath);

			// read property imageMagickConvertPath
			imageMagickConvertPath = properties.getProperty("imageMagickConvertPath", null);
			if (imageMagickConvertPath == null) {
				throw new InvalidAttributeValueException("imageMagickConvertPath parameter is null!");
			}
			log.info("use imageMagickConvertPath: " + imageMagickConvertPath);

			// read property originalIPhoneDataPath
			originalIPhoneDataPath = properties.getProperty("originalIPhoneDataPath", null);
			if (originalIPhoneDataPath == null) {
				throw new InvalidAttributeValueException("originalIPhoneDataPath parameter is null!");
			}
			log.info("use originalIPhoneDataPath: " + originalIPhoneDataPath);

			// read property photosDataPath
			outputPhotosDataPath = properties.getProperty("outputPhotosDataPath", null);
			if (outputPhotosDataPath == null) {
				throw new InvalidAttributeValueException("outputPhotosDataPath parameter is null!");
			}
			log.info("use outputPhotosDataPath: " + outputPhotosDataPath);

		} catch (FileNotFoundException e) {
			log.error("Can't find file: " + filename);
			throw e;
		} catch (IOException e) {
			log.error("Can't load file: " + filename);
			throw e;
		} catch (InvalidAttributeValueException e) {
			log.error("Error: " + e.getMessage());
			throw e;
		}
		log.debug("loadSettings() finished");
	}

	private void createTempFolder() {
		log.debug("start createTempFolder()");

		tempDir = Files.createTempDir();

		log.debug("delete temp folder: " + tempDir.getAbsolutePath() + " on exit");
		tempDir.deleteOnExit();

		log.debug("createTempFolder(); finished");
	}

	public void convertIphoneImages() {
		log.debug("start convertIphoneImages()");

		String origImgExt = ".heic";
		String newImgExt = ".jpg";
		String origVideoExt = ".mov";
		String newVideoExt = ".mp4";

		input = SardineFactory.begin(inputConnection.getUsername(), inputConnection.getPassword());
		String userFolder = inputConnection.getUserFolder();
		String startInputFolder = userFolder + originalIPhoneDataPath;

		try {
			List<DavResource> resourcesInput = getResourcesList(input, inputConnection, startInputFolder, 3);
			List<DavResource> allInput = getResourcesList(input, inputConnection, userFolder, -1);

			for (DavResource davResource : resourcesInput) {

				if (!davResource.isDirectory() && (davResource.getName().endsWith(origImgExt)
						|| davResource.getName().endsWith(origVideoExt))) {

					if (davResource.getName().endsWith(origImgExt)) {
						allInput = convertMedia(origImgExt, newImgExt, startInputFolder, userFolder, allInput,
								davResource);
					} else if (davResource.getName().endsWith(origVideoExt)) {
						allInput = convertMedia(origVideoExt, newVideoExt, startInputFolder, userFolder, allInput,
								davResource);
					}
				}
			}
		} catch (IOException e) {
			log.error(e);
		}

		log.debug("convertIphoneImages() finished");
	}

	private List<DavResource> convertMedia(String origExt, String newExt, String startInputFolder, String userFolder,
			List<DavResource> allInput, DavResource davResource) throws IOException, FileNotFoundException {

		String outputFolder = userFolder + outputPhotosDataPath;
		String mediaName = davResource.getName().replace(origExt, newExt);
		boolean existMedia = false;
		for (DavResource davResource2 : allInput) {
			if (davResource2.getName().equals(mediaName)) {
				log.debug("Found: " + mediaName + " in: " + davResource2.getPath());
				existMedia = true;
				break;
			}
		}

		if (!existMedia) {
			String getUrl = createEncodedUrl(inputConnection, davResource.getPath(), false);
			InputStream inputStream = input.get(getUrl);

			log.info("write input resource: " + davResource.getPath() + " ("
					+ getFormatedContentLength(davResource.getContentLength()) + ") into temporarly file");
			File tmpInputFile = writeStreamIntoFile(davResource, inputStream);

			File tmpMediaFile = new File(tmpInputFile.getAbsolutePath().replaceAll(origExt, newExt));

			ProcessBuilder processBuilder = null;

			if (origExt.equals(".heic")) {
				processBuilder = new ProcessBuilder(imageMagickConvertPath, tmpInputFile.getAbsolutePath(),
						tmpMediaFile.getAbsolutePath());
			} else if (origExt.equals(".mov")) {
				processBuilder = new ProcessBuilder(ffmpegPath, "-i", tmpInputFile.getAbsolutePath(), "-vcodec", "h264",
						"-acodec", "aac", tmpMediaFile.getAbsolutePath());
			} else {
				log.warn("Wrong media type");
				return allInput;
			}

			Process process = processBuilder.start();

			while (process.isAlive()) {
				try {
					log.debug("Wait 1sec to finish media transformation...");
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					log.error(e);
				}
			}
			log.info("Media-File created: " + tmpInputFile.getAbsolutePath() + " => " + process.exitValue());

			String putUrl = createEncodedUrl(inputConnection,
					davResource.getPath().replaceAll(origExt, newExt).replaceAll(startInputFolder, outputFolder), true);

			String monthDirUrl = putUrl.substring(0, putUrl.lastIndexOf("/"));
			String monthDirPath = monthDirUrl.substring(monthDirUrl.indexOf(userFolder) - 1) + "/";

			String yearDirUrl = monthDirUrl.substring(0, monthDirUrl.lastIndexOf("/"));
			String yearDirPath = yearDirUrl.substring(yearDirUrl.indexOf(userFolder) - 1) + "/";

			log.info("check if month directory: " + monthDirPath + " exist...");

			boolean updateAllInput = false;
			if (!existDirectory(allInput, yearDirPath)) {
				log.info("create new directory: " + yearDirUrl);
				input.createDirectory(yearDirUrl);
				updateAllInput = true;
			}
			if (!existDirectory(allInput, monthDirPath)) {
				log.info("create new directory: " + monthDirUrl);
				input.createDirectory(monthDirUrl);
				updateAllInput = true;
			}

			log.info("write temporarly file into output resource: " + putUrl);
			input.put(putUrl, tmpMediaFile, davResource.getContentType());

			log.info("delete temporarly file: " + tmpInputFile.getPath());
			tmpInputFile.delete();
			log.info("delete temp media file: " + tmpMediaFile.getPath());
			tmpMediaFile.delete();

			if (updateAllInput) {
				return getResourcesList(input, inputConnection, userFolder, -1);
			}
		}
		return allInput;
	}

	private List<DavResource> getResourcesList(Sardine sardine, ConnectionSettings settings, String folder, int depth)
			throws IOException {
		log.debug("start getResourcesList()");

		String urlInput = createEncodedUrl(settings, folder, false);
		List<DavResource> resources = sardine.list(urlInput, depth);

		log.debug("getResourcesList() finished");
		return resources;
	}

	private String createEncodedUrl(ConnectionSettings settings, String folderPath, boolean addSubfolder) {
		log.debug("start createEncodedUrl()");
		String url = settings.getFullFolderUri(folderPath, addSubfolder);
		url = encodeUrl(url);
		log.debug("createEncodedUrl() finished with url: " + url);
		return url;
	}

	// this it currently a bad way to encodeUrl, refactoring needed
	private static String encodeUrl(String url) {
		return url.replace("%", "%25").replace(" ", "%20").replace("{", "%7B").replace("}", "%7D").replace("[", "%5B")
				.replace("]", "%5D").replace("#", "%23").replace("`", "%60");
	}

	private String getFormatedContentLength(long contentLength) {
		int unit = 0;
		for (; contentLength > 1048576; contentLength >>= 10) {
			unit++;
		}
		if (contentLength > 1024) {
			unit++;
		}
		return String.format("%.3f %cB", contentLength / 1024f, " kMGTPE".charAt(unit));
	}

	private File writeStreamIntoFile(DavResource inputResources, InputStream inputStream)
			throws FileNotFoundException, IOException {
		log.debug("start writeStreamIntoFile()");

		File tmpInputFile = new File(
				tempDir.getAbsolutePath() + File.pathSeparator.replace(":", "/") + inputResources.getName());

		Long freeSpace = FileSystemUtils.freeSpaceKb(tempDir.getAbsolutePath()) * 1024;
		log.debug("free tmp space: " + freeSpace);
		if (freeSpace < inputResources.getContentLength()) {
			log.warn("not enough space ( " + freeSpace + " ) for: " + inputResources.getPath() + " size: "
					+ inputResources.getContentLength());
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

	private boolean existDirectory(List<DavResource> allInput, String dirPath) {
		for (DavResource davResource2 : allInput) {
			if (davResource2.isDirectory() && davResource2.getPath().equals(dirPath)) {
				log.debug("Found: " + dirPath);
				return true;
			}
		}
		return false;
	}
}
