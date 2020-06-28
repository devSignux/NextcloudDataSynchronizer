package space.signux;
import java.util.Enumeration;
import java.util.Properties;

import org.apache.log4j.Logger;
import org.jasypt.encryption.pbe.StandardPBEStringEncryptor;
import org.jasypt.exceptions.EncryptionOperationNotPossibleException;

public class Encryptor {

	private static Logger log = Logger.getLogger(Encryptor.class);

	private static String cryptoPassword = "jWsoKAkKygYCe8QSvgAH";

	private StandardPBEStringEncryptor encryptor = new StandardPBEStringEncryptor();

	public Encryptor() {
		encryptor.setPassword(cryptoPassword);
	}

	public void encryptPasswords(Properties properties) {
		Enumeration<?> propertyNames = properties.propertyNames();
		while (propertyNames.hasMoreElements()) {
			String key = (String) propertyNames.nextElement();
			if (key.toLowerCase().contains("password")) {
				String value = properties.getProperty(key);
				try {
					value = encryptor.decrypt(value);
				} catch (EncryptionOperationNotPossibleException ex) {
					// do nothing password is not encrypted
				}

				String encryptedPassword = encryptor.encrypt(value);
				log.info("Encrypt " + key + "...");
				properties.setProperty(key, encryptedPassword);
			}
		}
	}

	public String decrypt(String encryptedPassword) {
		return encryptor.decrypt(encryptedPassword);
	}
}
