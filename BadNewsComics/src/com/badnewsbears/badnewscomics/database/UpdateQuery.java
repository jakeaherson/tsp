package com.badnewsbears.badnewscomics.database;

public class UpdateQuery {
	
	private String file, set, distro;
	
	public UpdateQuery(String file) {
		this.file = file;
		set = "default";
		distro = "current_version";
	}
	
	public String getFile() {
		return file;
	}
	
	public void setSet(String set) {
		this.set = set;
	}
	
	public String getSet() {
		return set;
	}
	
	public void setDistro(String distro) {
		this.distro = distro;
	}
	
	public String getDistro() {
		return distro;
	}
}
