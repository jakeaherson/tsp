package com.badnewsbears.badnewscomics.database;

public class InitializationException extends Exception {
	public InitializationException(Exception inner) {
		super("Error initializing UpdateManager", inner);
	}
	
	public InitializationException() {
		super("Error initializing UpdateManager");
	}
}