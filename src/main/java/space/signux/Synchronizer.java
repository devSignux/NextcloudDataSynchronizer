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
		if (!synchronizer.loadSettings(args)) {
			return;
		}

	}

	private boolean loadSettings(String[] args) {

		String filename = "settings.txt";
		if (args.length > 0) {
			filename = args[0];
		}
		File settingsFile = new File(filename);

		if (!settingsFile.exists()) {
			System.out.println("settings file: " + filename + " doesn't exist!");
			return false;
		}

		FileInputStream in;
		try {
			System.out.println("load settings from file: " + filename);
			in = new FileInputStream(settingsFile);
			Properties properties = new Properties();
			properties.load(in);
			inputConnection = SettingsReader.CreateInputConnectionSettings(properties);
			outputConnection = SettingsReader.CreateOutputConnectionSettings(properties);
		} catch (FileNotFoundException e) {
			System.out.println("Can't find file: " + filename);
			return false;
		} catch (IOException e) {
			System.out.println("Can't load file: " + filename);
			return false;
		}
		return true;
	}
}
