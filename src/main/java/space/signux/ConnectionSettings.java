package space.signux;

public class ConnectionSettings {
	private String username;
	private String password;
	private String uri;
	private String subfolder;

	public String getUsername() {
		return username;
	}

	public void setUsername(String username) {
		this.username = username;
	}

	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		this.password = password;
	}

	public String getUri() {
		return uri;
	}

	public void setUri(String uri) {
		this.uri = uri;
	}

	public String getSubfolder() {
		return subfolder;
	}

	public void setSubfolder(String subfolder) {
		this.subfolder = subfolder;
	}

	public String getUserFolder() {
		String userFolder = "remote.php/dav/files/" + username + "/";
		if (subfolder != null && !subfolder.isEmpty()) {
			userFolder = subfolder + "/" + userFolder;
		}
		return userFolder;
	}

	public String getFullFolderUri(String folder, boolean addSubfolder) {
		if (addSubfolder && subfolder != null && !subfolder.isEmpty()) {
			return uri + "/" + subfolder + folder;
		}
		return uri + "/" + folder;
	}

	public String getBaseFolder() {
		return "/remote.php/dav/files/" + username + "/";
	}

	public String getBaseUrl() {
		return uri;
	}
}
