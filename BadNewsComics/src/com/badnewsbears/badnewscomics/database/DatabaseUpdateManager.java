package com.badnewsbears.badnewscomics.database;

import java.io.File;

public interface DatabaseUpdateManager {
	
	public void onInitialize() throws InitializationException;
	
	public int getCurrentVersion();
	
	public boolean needsUpdate(int oldVersion, int newVersion);
	
	public void onCreate(File file) throws CreationException;
	
	public void onUpgrade(File file, int oldVersion, int newVersion) throws UpgradeException;
}
