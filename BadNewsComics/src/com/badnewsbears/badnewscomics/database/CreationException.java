package com.badnewsbears.badnewscomics.database;

public class CreationException extends DatabaseException {
	public CreationException(Exception inner) {
		super("Error creating intitial database", inner);
	}
	
	public CreationException(final Exception inner, final int severity) {
		super("Error creating initial database", inner, severity);
	}
}
