package space.signux;

import java.util.Properties;

public class SettingsReader {

	public static ConnectionSettings CreateInputConnectionSettings(Properties properties) {
		return CreateConnectionSettings(properties, "input");
	}

	public static ConnectionSettings CreateOutputConnectionSettings(Properties properties) {
		return CreateConnectionSettings(properties, "output");
	}
	
	private static ConnectionSettings CreateConnectionSettings(Properties properties, String direction) {
		ConnectionSettings connection = new ConnectionSettings();
		connection.setUri(properties.getProperty(direction+"Uri", ""));
		connection.setSubfolder(properties.getProperty(direction+"Subfolder", ""));
		connection.setUsername(properties.getProperty(direction+"Username", ""));
		connection.setPassword(properties.getProperty(direction+"Password", ""));
		return connection;
	}
}
