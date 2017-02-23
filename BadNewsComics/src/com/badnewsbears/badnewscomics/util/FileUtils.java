package com.badnewsbears.badnewscomics.util;

import java.io.File;

import android.content.Context;

public final class FileUtils {
	private static FileUtils _instance;
	
	public static FileUtils getInstance(final Context context) {
		if (_instance == null) {
			_instance = new FileUtils(context);
		}
		
		return _instance;
	}
	
	public static FileUtils getInstance() {
		if (_instance == null) {
			throw new RuntimeException("FileUtils was never initialized");
		} 
		
		return _instance;
	}
	
	public static File getCacheDirectory(final Context context) {
		return context.getDir("temp", Context.MODE_PRIVATE);
	}
	
	public static File getCacheDirectory(final Context context, final Class clazz) {
		final String cname = clazz.getName();
		final String relativePath = cname.replace('.', '/');
		final File absolutePath = new File(getCacheDirectory(context), relativePath);
		return absolutePath;
	}
	
	private final Context _context;
	
	private FileUtils(final Context context) { 
		_context = context;
	}
	
	public File getCacheDirectory(final Class clazz) {
		return FileUtils.getCacheDirectory(_context, clazz);
	}
	
	public File getCacheDirectory() {
		return FileUtils.getCacheDirectory(_context);
	}
}
