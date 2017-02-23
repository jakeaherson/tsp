package com.badnewsbears.badnewscomics.database;

public class DatabaseException extends Exception {

    /** @see */
    public static final int 
    	E_FATAL		= 1, 
    	E_WARNING	= 2;
    
    public static String getErrorName(final int severity) {
    	switch (severity) {
	    	case E_FATAL:
	    		return "E_FATAL";
	    	case E_WARNING:
	    		return "E_WARNING";
	    	default:
	    		return "UNKNOWN";
    	}
    }
    
    private static String getErrorTag(final int severity, final String message) {
    	return "[" + getErrorName(severity) + "] " + message;
    }
    
    private final int severity;
    
    public DatabaseException(final String message, final Exception inner) {
    	this(message, inner, E_FATAL);
    }
    
    public DatabaseException(final String message, final Exception inner, final int severity) {
    	super(getErrorTag(severity, message), inner);
    	this.severity = severity;
    }
    
    public int getSeverity() {
    	return severity;
    }
}
