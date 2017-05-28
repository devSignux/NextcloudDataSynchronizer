package space.signux;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Properties;

public class Synchronizer {

	private ConnectionSettings inputConnection;
	private ConnectionSettings outputConnection;

	public static void main(String[] args) {
		Synchronizer synchronizer = new Synchronizer();
		synchronizer.loadSettings();
	}

	private void loadSettings() {
		String filename = "settings.txt";
		File propertiesFile = new File(filename);
		FileInputStream in;
		try {
			in = new FileInputStream(propertiesFile);
			Properties properties = new Properties();
			properties.load(in);
			inputConnection = SettingsReader.CreateInputConnectionSettings(properties);
			outputConnection = SettingsReader.CreateOutputConnectionSettings(properties);
		} catch (FileNotFoundException e) {
			System.out.println("Can't find file " + filename);
		} catch (IOException e) {
			System.out.println("Can't load file " + filename);
		}

	}
}
