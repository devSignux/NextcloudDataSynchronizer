package space.signux.helper;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

public class Blacklist {

	public static Set<String> getDeleteBlacklistFromFile(String deleteBlacklistFile) {
		Set<String> deleteBlacklist = new HashSet<String>();
		if (deleteBlacklistFile != null) {
			File blacklistFile = new File(deleteBlacklistFile);
			if (blacklistFile.canRead() && blacklistFile.isFile()) {
				BufferedReader br = null;
				try {
					br = new BufferedReader(new FileReader(deleteBlacklistFile));
					String line = null;
					while ((line = br.readLine()) != null) {
						deleteBlacklist.add(line);
					}
				} catch (IOException e) {
					e.printStackTrace();
				} finally {
					if (br != null) {
						try {
							br.close();
						} catch (IOException e) {
						}
					}
				}
			}
		}
		return deleteBlacklist;
	}

	public static boolean deletePath(String path, Set<String> deleteBlacklist) {
		for(String notDeletePath : deleteBlacklist) {
			if(path.startsWith(notDeletePath)) {
				return false;
			}
		}
		return true;
	}
}
