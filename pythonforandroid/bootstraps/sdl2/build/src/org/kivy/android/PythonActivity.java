
package org.kivy.android;

import java.io.InputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.File;

import android.view.ViewGroup;
import android.view.SurfaceView;
import android.app.Activity;
import android.util.Log;
import android.widget.Toast;
import android.os.Bundle;
import android.os.PowerManager;
import android.graphics.PixelFormat;
import android.view.SurfaceHolder;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.ApplicationInfo;

import org.libsdl.app.SDLActivity;

import org.renpy.android.ResourceManager;
import org.renpy.android.AssetExtract;


public class PythonActivity extends SDLActivity {
    private static final String TAG = "PythonActivity";

    public static PythonActivity mActivity = null;
    
    private ResourceManager resourceManager = null;
    private Bundle mMetaData = null;
    private PowerManager.WakeLock mWakeLock = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.v(TAG, "My oncreate running");
        resourceManager = new ResourceManager(this);

        Log.v(TAG, "Ready to unpack");
        unpackData("private", getFilesDir());

        Log.v(TAG, "About to do super onCreate");
        super.onCreate(savedInstanceState);
        Log.v(TAG, "Did super onCreate");
        
        this.mActivity = this;
        
        String mFilesDirectory = mActivity.getFilesDir().getAbsolutePath();
        Log.v(TAG, "Setting env vars for start.c and Python to use");
        SDLActivity.nativeSetEnv("ANDROID_PRIVATE", mFilesDirectory);
        SDLActivity.nativeSetEnv("ANDROID_ARGUMENT", mFilesDirectory);
        SDLActivity.nativeSetEnv("ANDROID_APP_PATH", mFilesDirectory);
        SDLActivity.nativeSetEnv("PYTHONHOME", mFilesDirectory);
        SDLActivity.nativeSetEnv("PYTHONPATH", mFilesDirectory + ":" + mFilesDirectory + "/lib");

        
        // nativeSetEnv("ANDROID_ARGUMENT", getFilesDir());

        try {
            Log.v(TAG, "Access to our meta-data...");
            this.mMetaData = this.mActivity.getPackageManager().getApplicationInfo(
                    this.mActivity.getPackageName(), PackageManager.GET_META_DATA).metaData;

            PowerManager pm = (PowerManager) this.mActivity.getSystemService(Context.POWER_SERVICE);
            if ( this.mMetaData.getInt("wakelock") == 1 ) {
                this.mWakeLock = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK, "Screen On");
            }
            if ( this.mMetaData.getInt("surface.transparent") != 0 ) {
                Log.v(TAG, "Surface will be transparent.");
                getSurface().setZOrderOnTop(true);
                getSurface().getHolder().setFormat(PixelFormat.TRANSPARENT);
            } else {
                Log.i(TAG, "Surface will NOT be transparent");
            }
        } catch (PackageManager.NameNotFoundException e) {
        }
    }
    
    // This is just overrides the normal SDLActivity, which just loads
    // SDL2 and main
    protected String[] getLibraries() {
        return new String[] {
            "SDL2",
            "SDL2_image",
            "SDL2_mixer",
            "SDL2_ttf",
            "main"
        };
    }
    
    public void loadLibraries() {
        // AND: This should probably be replaced by a call to super
        for (String lib : getLibraries()) {
            System.loadLibrary(lib);
        }

        try {
            System.loadLibrary("python2.7");
                } catch(UnsatisfiedLinkError e) {
            Log.v(TAG, "Failed to load libpython2.7");
        }

        try {
            System.loadLibrary("python3.5m");
                } catch(UnsatisfiedLinkError e) {
            Log.v(TAG, "Failed to load libpython3.5m");
        }
        
        // System.load(getFilesDir() + "/lib/python2.7/lib-dynload/_io.so");
        // System.load(getFilesDir() + "/lib/python2.7/lib-dynload/unicodedata.so");
        
        try {
            // System.loadLibrary("ctypes");
            System.load(getFilesDir() + "/lib/python2.7/lib-dynload/_ctypes.so");
        } catch(UnsatisfiedLinkError e) {
            Log.v(TAG, "Unsatisfied linker when loading ctypes");
        }

        Log.v(TAG, "Loaded everything!");
    }
    
    public void recursiveDelete(File f) {
        if (f.isDirectory()) {
            for (File r : f.listFiles()) {
                recursiveDelete(r);
            }
        }
        f.delete();
    }

    /**
     * Show an error using a toast. (Only makes sense from non-UI
     * threads.)
     */
    public void toastError(final String msg) {

        final Activity thisActivity = this;

        runOnUiThread(new Runnable () {
            public void run() {
                Toast.makeText(thisActivity, msg, Toast.LENGTH_LONG).show();
            }
        });

        // Wait to show the error.
        synchronized (this) {
            try {
                this.wait(1000);
            } catch (InterruptedException e) {
            }
        }
    }
    
    public void unpackData(final String resource, File target) {
        
        Log.v(TAG, "UNPACKING!!! " + resource + " " + target.getName());
        
        // The version of data in memory and on disk.
        String data_version = resourceManager.getString(resource + "_version");
        String disk_version = null;
        
        Log.v(TAG, "Data version is " + data_version);

        // If no version, no unpacking is necessary.
        if (data_version == null) {
            return;
        }

        // Check the current disk version, if any.
        String filesDir = target.getAbsolutePath();
        String disk_version_fn = filesDir + "/" + resource + ".version";

        try {
            byte buf[] = new byte[64];
            InputStream is = new FileInputStream(disk_version_fn);
            int len = is.read(buf);
            disk_version = new String(buf, 0, len);
            is.close();
        } catch (Exception e) {
            disk_version = "";
        }

        // If the disk data is out of date, extract it and write the
        // version file.
        // if (! data_version.equals(disk_version)) {
        if (! data_version.equals(disk_version)) {
            Log.v(TAG, "Extracting " + resource + " assets.");

            recursiveDelete(target);
            target.mkdirs();

            AssetExtract ae = new AssetExtract(this);
            if (!ae.extractTar(resource + ".mp3", target.getAbsolutePath())) {
                toastError("Could not extract " + resource + " data.");
            }

            try {
                // Write .nomedia.
                new File(target, ".nomedia").createNewFile();

                // Write version file.
                FileOutputStream os = new FileOutputStream(disk_version_fn);
                os.write(data_version.getBytes());
                os.close();
            } catch (Exception e) {
                Log.w("python", e);
            }
        }
    }
    
    public static ViewGroup getLayout() {
        return   mLayout;
    }

    public static SurfaceView getSurface() {
        return   mSurface;
    }
}
