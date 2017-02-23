package com.badnewsbears.badnewscomics.database;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.os.Environment;
import android.os.StatFs;
import android.util.Log;

/**
 * Database Manager capable of handling multiple database files & their storage.
 * Use this in place of SQLiteOpenHelper when more exact handling of file storage
 * location is required.
 */
@SuppressWarnings("unused")
public class DatabaseManager {
	
	/* TODO
	 * - Database file version caching ( no file checking )
	 * - Download to temp file then copy over
	 * + Available storage space
	 * + MD5 checksums
	 * ~ DatabaseUpdateManager error severities
	 */
	
    private static final String TAG = DatabaseManager.class.getSimpleName();
    
	public static final int 
		STORAGE_MODE_DEVICE   = 0,
		STORAGE_MODE_EXTERNAL = 1;
	
	public static final int 
		STORAGE_STATE_READWRITE   = 0,
		STORAGE_STATE_READONLY    = 1,
		STORAGE_STATE_UNAVAILABLE = 2;
	
	private static final String PREFERENCE_FILE_NAME 	  = "dbmanager_preferences";
	private static final String KEY_STORAGE_MODE_CURRENT  = "dbmanager_storage_mode_current";
	private static final String KEY_STORAGE_MODE_PREVIOUS = "dbmanager_storage_mode_previous";
	private static final String KEY_TRANSFER_SUCCESS 	  = "dbmanager_transfer_success";

	private File _externalPath; // external (sd) database directory
    private File _devicePath;   // device application storage
    private int _storageMode;
    private int _externalStorageState;
    
    protected SQLiteDatabase _database;
 
    private final Context _context;
    
    private FilenameFilter _fileFilter = new DatabaseFileFilter();
    
    private class DatabaseFileFilter implements FilenameFilter {
		@Override
		public boolean accept(File dir, String filename) {
			return filename.endsWith(".s3db") || filename.endsWith(".csm");
		}
    }
    
    private ArrayList<OnTableChangedListener> _changeListeners;
    
    /**
     * Initializes new DatabaseManager instance with the given context. If this is the first
     * instance of a DatabaseManager for this context then the storage-mode will default to
     * STORAGE_MODE_DEVICE & all created databases will be stored accordingly. The storage
     * mode can be changed via a call to {@link #setStorageMode(int)}.
     * @param context Application context needed to access file-system & preferences
     */
    public DatabaseManager(Context context) {
    	_context = context;
    	_devicePath = context.getDir("databases", Context.MODE_PRIVATE);
    	
    	refreshExternalState();
    	
    	readStorageMode();
    }
    
    /**
     * @return false if the last file transfer between storage locations was unsuccessful
     */
    public boolean lastTransferSuccess() {
    	SharedPreferences sp = _context.getSharedPreferences(
                PREFERENCE_FILE_NAME, Context.MODE_PRIVATE
        );
    	return sp.getBoolean(KEY_TRANSFER_SUCCESS, true);
    }
    
    /**
     * @return The storage mode for databases used by this application.
     */
    public int getStorageMode() {
    	return _storageMode;
    }
    
    /**
     * Reads saved storage mode. Call this if multiple DatabaseManager
     * instances are being used simultaneously to ensure that
     * the DatabaseManager is searching the correct directory for stored
     * databases.
     */
    public void readStorageMode() {
    	SharedPreferences sp = _context.getSharedPreferences(
                PREFERENCE_FILE_NAME, Context.MODE_PRIVATE
        );
    	_storageMode = sp.getInt(KEY_STORAGE_MODE_CURRENT, STORAGE_MODE_DEVICE);
    }

    /**
     * @return Current state of access to database files.
     */
    public int getStorageState() {
    	switch (_storageMode) {
	    	case STORAGE_MODE_DEVICE:
	    		return STORAGE_STATE_READWRITE;
	    	case STORAGE_MODE_EXTERNAL:
	    		return _externalStorageState;
    	}
    	return STORAGE_STATE_UNAVAILABLE;
    }
    
    /**
     * Syncs external storage state &amp; external file path.
     * @return The new storage state
     */
    public int refreshExternalState() {
    	_externalPath = _context.getExternalFilesDir(null);
    	
    	String state = Environment.getExternalStorageState();
    	if (state.equals(Environment.MEDIA_MOUNTED))
    		_externalStorageState = STORAGE_STATE_READWRITE;
    	else if (state.equals(Environment.MEDIA_MOUNTED_READ_ONLY))
    		_externalStorageState = STORAGE_STATE_READONLY;
    	else
    		_externalStorageState = STORAGE_STATE_UNAVAILABLE;
    	
    	return _externalStorageState;
    }
    
    /**
     * @return The available space in bytes on the current storage directory.
     * @see #getAvailableSpace(int)
     */
    public long getAvailableSpace() {
    	return this.getAvailableSpace(_storageMode);
    }
    
    /**
     * @param storageMode One of {@link DatabaseManager#STORAGE_MODE_DEVICE},
     * {@link DatabaseManager#STORAGE_MODE_EXTERNAL}
     * @return The available space in bytes on the specified storage directory.
     * @see #getAvailableSpace()
     */
    public long getAvailableSpace(final int storageMode) {
    	StatFs stat;
    	switch (storageMode) {
	    	case STORAGE_MODE_DEVICE:
	    		stat = new StatFs(_devicePath.getAbsolutePath());
	    		break;
	    	case STORAGE_MODE_EXTERNAL:
	    		if (_externalStorageState == STORAGE_STATE_UNAVAILABLE)
	    			return 0L;
	    		else 
	    			stat = new StatFs(_externalPath.getAbsolutePath());
	    		break;
	    	default:
	    		throw new IllegalArgumentException(
                        "Invalid storage mode, must be one of " +
                        "{ STORAGE_MODE_DEVICE, STORAGE_MODE_EXTERNAL }"
                );
    	}

    	return ( (long)stat.getBlockSize() * (long)stat.getAvailableBlocks() );
    }
    
    /**
     * Checks to see if the database file exists in the current storage directory.
     * @param dbName Name of database.
     * @return true if the database file exists.
     */
    public boolean checkDatabaseExists(String dbName) {
    	switch (_storageMode) {
            case STORAGE_MODE_DEVICE:
                return new File(_devicePath, dbName + ".s3db").exists();
            case STORAGE_MODE_EXTERNAL:
                if (_externalPath == null) {
                    return false;
                } else {
                    return new File(_externalPath, dbName + ".s3db").exists();
                }
    	}
    	return false;
    }
    
    /**
     * Gets the version of the database file.
     * @param dbName Name of database
     * @return The version of the database or -1 if it does not exist
     */
    public int getDatabaseVersion(String dbName) {
    	File dbFile = null;
    	switch (_storageMode) {
    		case STORAGE_MODE_DEVICE:
    			dbFile = new File(_devicePath, dbName + ".s3db");
    			break;
    		case STORAGE_MODE_EXTERNAL:
    			dbFile = new File(_externalPath, dbName + ".s3db");
    			break;
            default:
                return -1;
    	}
    	
		if (!dbFile.exists()) {
			return -1;
		} else {
			int version;
			SQLiteDatabase db = SQLiteDatabase.openDatabase(
                    dbFile.getAbsolutePath(),
                    null, SQLiteDatabase.OPEN_READONLY
            );
			version = db.getVersion(); 
			db.close();
			return version;
		}
    }
    
    /**
     * Calls runUpdates() on a new thread without blocking execution. Especially 
     * recommended when the database is being pulled from an online source.
     * @param dbName Name of the database
     * @param manager UpdateManager
     * @param listener Completion callback listener
     * @see #runUpdates(String dbName, DatabaseUpdateManager manager)
     */
    public void runUpdatesAsync(final String dbName, final DatabaseUpdateManager manager,
            final OnUpdateCompleteListener listener) {
    	Thread t = new Thread( new Runnable() {
			@Override
			public void run() {
				try {
					runUpdates(dbName, manager);
					listener.onComplete();
				} catch (Exception e) {
					listener.onError(e);
				}
			}
    	});
    	t.start();
    }
    
    /**
     * Runs required updates for specified database. Calls the supplied
     * UpdateManager's onCreate() method if the database does not exist.
     * Otherwise calls getCurrentVersion() then onUpdate() if the
     * current version is newer.</br></br>
     * Note - The UpdateManager is responsible for setting the database version
     * @param dbName Name of the database
     * @param manager UpdateManager
     * @throws IOException If the database file could not be created
     * @throws CreationException Dependent on UpdateManager implementation
     * @throws UpgradeException Dependent on UpdateManager implementation
     * @throws InitializationException Dependent on UpdateManager implementation
     */
    @SuppressWarnings("ResultOfMethodCallIgnored")
    public void runUpdates(String dbName, DatabaseUpdateManager manager)
            throws IOException, CreationException, UpgradeException, InitializationException {
    	
    	manager.onInitialize();
    	
    	File dbFile;
    	switch (_storageMode) {
    		case STORAGE_MODE_DEVICE:
    			dbFile = new File(_devicePath, dbName + ".s3db");
    			break;
    		case STORAGE_MODE_EXTERNAL:
    			dbFile = new File(_externalPath, dbName + ".s3db");
    			break;
            default:
                throw new IllegalStateException("Unknown storage mode");
    	}

		if (!dbFile.exists()) {
			dbFile.createNewFile();
			manager.onCreate(dbFile);
		} else {
			int oldVersion, newVersion;
			SQLiteDatabase db = SQLiteDatabase.openOrCreateDatabase(dbFile, null);
			oldVersion = db.getVersion(); 
			newVersion = manager.getCurrentVersion();
			db.close();
			if (oldVersion < newVersion)
				if (manager.needsUpdate(oldVersion, newVersion))
					manager.onUpgrade(dbFile, oldVersion, newVersion);
		}
    }
    
    /**
     * Gets a writable file from either application or external storage.
     * If the file does not exist it will be created.
     * @param dbName Name of the file
     * @return Resulting file
     * @throws IOException If the file cannot be retrieved
     * @see {@link #getFile(String, boolean)}
     */
    protected File getFile(String dbName) throws IOException {
    	return getFile(dbName, true);
    }

    /**
     * Gets a writable file from either application or external storage.
     * @param dbName Name of the file
     * @param create True to create the file if it does not exist
     * @return Resulting file
     * @throws IOException If the file cannot be retrieved
     * @see {@link #getFile(String)}
     */
    @SuppressWarnings("ResultOfMethodCallIgnored")
    protected File getFile(String dbName, boolean create) throws IOException {
    	File ret = null;
    	switch (_storageMode) {
    		case STORAGE_MODE_DEVICE:
    			ret = new File(_devicePath, dbName + ".s3db");
    			if (!ret.exists() && create)
    				ret.createNewFile();
    			break;
    		case STORAGE_MODE_EXTERNAL:
    			ret = new File(_externalPath, dbName + ".s3db");
    			if (!ret.exists() && create)
    				ret.createNewFile();
    			break;
    	}

    	return ret;
    }
    
    /**
     * Gets a writable database file from either application or external storage.
     * If the file does not exist it will be created.
     * @param dbName Name of the database file
     * @return Resulting database file
     * @throws IOException If the file cannot be retrieved
     */
    public SQLiteDatabase getDatabase(String dbName) throws IOException {
    	File file = getFile(dbName);
    	return SQLiteDatabase.openDatabase(
    			file.getAbsolutePath(), null, SQLiteDatabase.CREATE_IF_NECESSARY);
    }
    
    /**
     * Deletes a database file from storage.
     * @param dbName Name of the file
     * @return true if delete was successful
     */
    @SuppressWarnings("ResultOfMethodCallIgnored")
    public boolean deleteDatabase(String dbName) {
    	File del;
    	switch (_storageMode) {
    		case STORAGE_MODE_DEVICE:
    			new File(_devicePath, dbName + ".csm").delete();
    			del = new File(_devicePath, dbName + ".s3db");
    			return del.delete();
    		case STORAGE_MODE_EXTERNAL:
    			new File(_externalPath, dbName + ".csm").delete();
    			del = new File(_externalPath, dbName + ".s3db");
    			return del.delete();
    	}

    	return false;
    }
    
    /**
     * Moves old files to current storage directory if the last 
     * transfer was unsuccessful.
     * @throws FileTransferException If the files were not moved 
     * to the new directory successfully.
     */
    @SuppressLint("CommitPrefEdits")
    public void reTransfer() throws FileTransferException {
    	SharedPreferences sp = _context.getSharedPreferences(
                PREFERENCE_FILE_NAME, Context.MODE_PRIVATE
        );
    	
    	if (sp.getBoolean(KEY_TRANSFER_SUCCESS, true)) 
    		return;
    				
    	int oldStorageMode = sp.getInt(KEY_STORAGE_MODE_PREVIOUS, 0);
    	
    	if (oldStorageMode == _storageMode)
    		return;
    	
    	boolean transferSuccess = true;
    	try {
    		transfer(oldStorageMode, _storageMode);
    	} catch (FileTransferException e) {
    		transferSuccess = false;
    		throw e;
    	} finally {
    		Editor e = sp.edit();
    		e.putBoolean(KEY_TRANSFER_SUCCESS, transferSuccess);
    		e.commit();
    	}
    	
    }
    
    @SuppressWarnings("ResultOfMethodCallIgnored")
    private void transfer(int oldStorageMode, int newStorageMode)
            throws FileTransferException {
    	try {
	    	// Get existing files
	    	Log.d(TAG, "Retrieving existing database files");
	    	File files[];
	    	switch (oldStorageMode) {
	    		case STORAGE_MODE_DEVICE:
	    			files = _devicePath.listFiles(_fileFilter);
	    			break;
	    		case STORAGE_MODE_EXTERNAL:
	    			files = _externalPath.listFiles(_fileFilter);
	    			break;
                default:
                    throw new IllegalStateException("Unknown storage mode");
	    	}
	    	
	    	// Copy to new directory
	    	Log.d(TAG, "Copying files to new storage directory");
	    	File out;
	    	FileChannel src, dst;
	    	switch (newStorageMode) {
	    		case STORAGE_MODE_DEVICE:
	    			for (File file : files) {
	    				out = new File(_devicePath, file.getName());
	    				out.createNewFile();
	    				src = new FileInputStream(file).getChannel();
	    				dst = new FileOutputStream(out).getChannel();
	    				dst.transferFrom(src, 0, src.size());
	    				src.close();
	    				dst.close();
	    			}
	    			break;
	    		case STORAGE_MODE_EXTERNAL:
	    			for (File file : files) {
	    				out = new File(_externalPath, file.getName());
	    				out.createNewFile();
	    				src = new FileInputStream(file).getChannel();
	    				dst = new FileOutputStream(out).getChannel();
	    				dst.transferFrom(src, 0, src.size());
	    				src.close();
	    				dst.close();
	    			}
	    			break;
	    	}
	    	
	    	// Delete old files
	    	Log.d(TAG, "Deleting old database files");
	    	for (File file : files) {
	    		file.delete();
	    	}
	    	
	    	Log.d(TAG, "Transfer success");
	    	
    	} catch (Exception e) {
    		throw new FileTransferException(e);
    	}
    }
    
    /**
     * Changes the default storage mode & transfers any existing 
     * database files to the new storage mode's directory
     * @param newStorageMode New storage mode, existing files will be copied here
     * @throws FileTransferException If the files were not moved to the new directory 
     * successfully. Note that the storage mode will still be changed regardless
     */
    @SuppressLint("CommitPrefEdits")
    public void setStorageMode(int newStorageMode) throws FileTransferException {
    	if (_storageMode == newStorageMode)
    		return;
    	
    	boolean transferSuccess = true;
    	try {
    		transfer(_storageMode, newStorageMode);
    	} catch (FileTransferException e) {
    		transferSuccess = false;
    		Log.e(TAG, "Error transfering files", e);
    		throw e;
    	} finally {
	    	// Set mode in preferences
	    	SharedPreferences sp = _context.getSharedPreferences(
                    PREFERENCE_FILE_NAME, Context.MODE_PRIVATE
            );
	    	Editor edit = sp.edit();
	    	edit.putInt(KEY_STORAGE_MODE_CURRENT, newStorageMode);
	    	edit.putInt(KEY_STORAGE_MODE_PREVIOUS, _storageMode);
	    	edit.putBoolean(KEY_TRANSFER_SUCCESS, transferSuccess);
	    	edit.commit();
	    	_storageMode = newStorageMode;
    	}
    }
    
    /**
     * Opens a database. If there is already a database open with this
     * DatabaseManager instance it will be closed.
     * @param dbName Name of the database to open
     * @throws SQLiteException If the file could not be opened
     */
    public void openDatabase(String dbName) throws SQLiteException {
    	if (_database != null && _database.isOpen())
    		_database.close();
    	
    	switch (_storageMode) {
	    	case STORAGE_MODE_DEVICE:
	    		_database = SQLiteDatabase.openDatabase(
                        new File(_devicePath, dbName + ".s3db").getAbsolutePath(),
                        null, SQLiteDatabase.OPEN_READWRITE
                );
	    		break;
	    	case STORAGE_MODE_EXTERNAL:
	    		_database = SQLiteDatabase.openDatabase(
                        new File(_externalPath, dbName + ".s3db").getAbsolutePath(),
                        null, SQLiteDatabase.OPEN_READWRITE
                );
	    		break;
    	}
    }
    
    /**
     * Closes current database instance.
     */
    public void close() {
    	if (_database != null && _database.isOpen()) {
    		_database.close();
    		_database = null;
    	}
    }
    
    public boolean isOpen() {
    	return _database != null && _database.isOpen();
    }
    
    /**
     * Registers a change listener to receive callbacks from this {@link DatabaseManager}.
     * Derivative classes are responsible for dispatching all change events.
     * @param listener {@link OnTableChangedListener} to register
     * @see {@link #unregisterChangeListener(OnTableChangedListener)}
     */
    public void registerChangeListener(OnTableChangedListener listener) {
    	if (_changeListeners == null)
    		_changeListeners = new ArrayList<OnTableChangedListener>();
    	
    	_changeListeners.add(listener);
    }
    
    /**
     * Unregisters a change listener associated with this {@link DatabaseManager}.
     * @param listener {@link OnTableChangedListener} to remove
     * @see {@link #registerChangeListener(OnTableChangedListener)}
     */
    public void unregisterChangeListener(OnTableChangedListener listener) {
    	if (_changeListeners != null)
    		_changeListeners.remove(listener);
    }
    
    protected void onTableChanged(String tableName) {
    	if (_changeListeners != null)
    		for (OnTableChangedListener listener : _changeListeners)
    			listener.onTableChanged(tableName);
    }

    //			        *********************************
    // ================ *           Checksums           * ==================
    //                  *********************************

    /**
     * Calculates an MD5 checksum on the given database file.
     * @param dbName Name of the database
     * @return The MD5 checksum of the file or null if it does not exist
     * @throws NoSuchAlgorithmException 
     * @throws IOException 
     */
    public String calcChecksum(final String dbName)
            throws NoSuchAlgorithmException, IOException {
    	final File dbFile = getFile(dbName, false);
    	
    	if (!dbFile.exists()) {
    		return null;
    	} else {
	    	final FileInputStream fis = new FileInputStream(dbFile);
	    	final MessageDigest md = MessageDigest.getInstance("MD5");
	    	final byte[] dataBytes = new byte[1024];
	     
	        int len = 0; 
	        while ((len = fis.read(dataBytes)) != -1) {
	        	md.update(dataBytes, 0, len);
	        }
	     
	        final byte[] mdbytes = md.digest();
	        
	        fis.close();
	     
	        // Convert the byte to hex format
	        final StringBuilder sb = new StringBuilder();
	        for (int i = 0; i < mdbytes.length; i++) {
	        	sb.append(Integer.toString(mdbytes[i] & 0xff, 16).substring(1));
	        }
	     
	    	return sb.toString();
    	}
    }
    
    /**
     * Calculates &amp; stores the MD5 checksum of the given database. 
     * The checksum file resides in the current database directory &amp; 
     * is named in the format: [dbName].csm.
     * @param dbName Name of the database
     * @throws NoSuchAlgorithmException
     * @throws IOException
     */
    @SuppressWarnings("ResultOfMethodCallIgnored")
    public void storeChecksum(final String dbName)
            throws NoSuchAlgorithmException, IOException {
    	final String checksum = calcChecksum(dbName);
    	
    	File ret;
    	switch (_storageMode) {
    		case STORAGE_MODE_DEVICE:
    			ret = new File(_devicePath, dbName + ".csm");
    			if (!ret.exists())
    				ret.createNewFile();
    			break;
    		case STORAGE_MODE_EXTERNAL:
    			ret = new File(_externalPath, dbName + ".csm");
    			if (!ret.exists())
    				ret.createNewFile();
    			break;
            default:
                throw new IllegalStateException("Unknown storage mode");
    	}
    	
    	final FileWriter ofstream = new FileWriter(ret);
    	ofstream.write(checksum);
    	ofstream.close();
    }
    
    /**
     * Loads a stored MD5 checksum for the given database.
     * @param dbName Name of the database
     * @return The loaded checksum or null if the .csm file is not found
     * @throws IOException
     */
    public String loadChecksum(final String dbName) throws IOException {
    	File ret;
    	switch (_storageMode) {
    		case STORAGE_MODE_DEVICE:
    			ret = new File(_devicePath, dbName + ".csm");
    			break;
    		case STORAGE_MODE_EXTERNAL:
    			ret = new File(_externalPath, dbName + ".csm");
    			break;
            default:
                throw new IllegalStateException("Unknown storage mode");
    	}
    	
    	if (!ret.exists()) {
    		return null;
    	} else {
	    	final FileReader ifstream = new FileReader(ret);
	    	
	    	final char[] buffer = new char[32];
	    	ifstream.read(buffer);
	    	final String checksum = new String(buffer);
	    	
	    	ifstream.close();
	    	
	    	return checksum;
    	}
    }
    
    /**
     * Compares the stored MD5 checksum of the given database file 
     * with its calculated checksum.
     * @param dbName Name of the database
     * @return False if the stored &amp; calculated checksums do not match
     * @throws IOException
     * @throws NoSuchAlgorithmException
     * @see {@link #checkIntegrity(String, String)}
     * @see {@link #loadChecksum(String)}
     * @see {@link #calcChecksum(String)}
     */
    public boolean checkIntegrity(final String dbName)
            throws IOException, NoSuchAlgorithmException {
    	final String storedChecksum = loadChecksum(dbName);
    	final String calcedChecksum = calcChecksum(dbName);
    	
    	return 
			calcedChecksum != null 
			&& storedChecksum != null 
			&& calcedChecksum.equalsIgnoreCase(storedChecksum);
    }

    /**
     * Calculates the MD5 checksum of the given database &amp; 
     * compares it with the specified value.
     * @param dbName Name of the database
     * @param checksum Expected checksum value as a 32 character hex string
     * @return False if the calculated checksum does not match the expected value
     * @throws IOException
     * @throws NoSuchAlgorithmException
     * @see {@link #checkIntegrity(String)}
     * @see {@link #loadChecksum(String)}
     * @see {@link #calcChecksum(String)}
     */
    public boolean checkIntegrity(final String dbName, final String checksum)
            throws IOException, NoSuchAlgorithmException {
    	final String calcedChecksum = calcChecksum(dbName);

    	return 
			calcedChecksum != null 
			&& checksum != null 
			&& calcedChecksum.equalsIgnoreCase(checksum);
    }
    
	//			        *********************************
	// ================ *          Exceptions           * ==================
	//                  *********************************

	public class FileTransferException extends Exception {
		public FileTransferException(Exception inner) {
			super("Error transfering database files", inner);
		}
	}

    //			        *********************************
    // ================ *           Listeners           * ==================
    //                  *********************************

    public interface OnUpdateCompleteListener {
		public void onComplete();
		
		public void onError(Exception e);
	}
	
	/**
	 * Provides callback methods for change events dispatched by a {@link DatabaseManager} instance.
	 */
	public interface OnTableChangedListener {
		public void onTableChanged(String tableName);
	}
 
}
