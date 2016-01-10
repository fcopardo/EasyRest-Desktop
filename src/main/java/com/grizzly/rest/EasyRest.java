package com.grizzly.rest;


import java.io.File;
import java.io.FileFilter;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.FutureTask;

/**
 * Created by fpardo on 12/18/14.
 * Utility class.
 */
public class EasyRest {

    private static boolean DebugMode = true;

    /**
     * Deletes the EasyRest cache.
     */
    public static void deleteCache(){

        class Task implements Runnable{

            String workingDir = System.getProperty("user.dir");

            @Override
            public void run() {
                File f = new File(workingDir + File.separator + "EasyRest");
                if(f.exists()){
                    for(File file: f.listFiles()){
                        file.delete();
                    }
                }
            }
        }

        Task myTask = new Task();
        FutureTask futureTask = new FutureTask(myTask, null);
        futureTask.run();

    }

    /**
     * Deletes the EasyRest cache of the specified types of answer, and older than @maximumTime
     * @param classes the response types to be deleted.
     * @param maximumTime The maximum caching time.
     */
    public static void deleteCache(List<Class> classes, long maximumTime){


        class Task implements Runnable{

            String workingDir = System.getProperty("user.dir");
            public List<Class> classes = new ArrayList<>();
            public long maximumTime;

            @Override
            public void run() {

                File f = new File(workingDir + File.separator + "EasyRest");

                FileFilter filter = new FileFilter() {
                    @Override
                    public boolean accept(File pathname) {
                        for(Class aClass : classes){
                            if(pathname.getName().contains(aClass.getSimpleName())
                                    && pathname.lastModified() > maximumTime){
                                return true;
                            }
                        }
                        return false;
                    }
                };

                if(f.exists()){
                    List<File> files = new ArrayList<>(Arrays.asList(f.listFiles(filter)));
                    for(File file: files){
                        file.delete();
                    }
                }

            }
        }

        Task myTask = new Task();
        FutureTask futureTask = new FutureTask(myTask, null);
        futureTask.run();

    }

    public static boolean checkConnectivity(){
        /*Context context = null;
        context = context.getApplicationContext();
        ConnectivityManager cm = (ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        boolean isConnected = activeNetwork != null && activeNetwork.isConnectedOrConnecting();
        context = null;
        return isConnected;*/
        return true;
    }

    public static void setDebugMode(boolean debugMode){
        DebugMode = debugMode;
    }

    public static boolean isDebugMode(){
        return DebugMode;
    }

    /**
     * Creates a SHA-1 hash from a given string.
     * @param password the string to be hashed.
     * @return a String representing the SHA-1 form of the argument.
     * @throws NoSuchAlgorithmException if the SHA-1 algorithm is absent from the JVM.
     */
    static String getHashOne(String password)
            throws NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        md.update(password.getBytes());

        byte byteData[] = md.digest();

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < byteData.length; i++) {
            sb.append(Integer.toString((byteData[i] & 0xff) + 0x100, 16)
                    .substring(1));
        }
        return sb.toString();
    }

}
