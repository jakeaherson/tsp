package com.badnewsbears.badnewscomics.database;

public class UpgradeException extends DatabaseException {
	public final int newVersion, oldVersion;
	
	public UpgradeException(final Exception inner, final int newVersion, final int oldVersion) {
		super("Error upgrading database from version " + oldVersion + " to version " + oldVersion, inner);
		this.newVersion = newVersion;
		this.oldVersion = oldVersion;
	}

	public UpgradeException(final Exception inner, final int newVersion, final int oldVersion, final int severity) {
		super("Error upgrading database from version " + oldVersion + " to version " + oldVersion, inner, severity);
		this.newVersion = newVersion;
		this.oldVersion = oldVersion;
	}
}
