package space.signux;

import java.util.Properties;

public class SettingsReader {

	public static ConnectionSettings CreateInputConnectionSettings(Properties properties, Encryptor encryptor) {
		return CreateConnectionSettings(properties, "input", encryptor);
	}

	public static ConnectionSettings CreateOutputConnectionSettings(Properties properties, Encryptor encryptor) {
		return CreateConnectionSettings(properties, "output", encryptor);
	}

	private static ConnectionSettings CreateConnectionSettings(Properties properties, String direction, Encryptor encryptor) {
		ConnectionSettings connection = new ConnectionSettings();
		connection.setUri(properties.getProperty(direction + "Uri", ""));
		connection.setSubfolder(properties.getProperty(direction + "Subfolder", ""));
		connection.setUsername(properties.getProperty(direction + "Username", ""));
		connection.setPassword(encryptor.decrypt(properties.getProperty(direction + "Password", "")));
		return connection;
	}
}
