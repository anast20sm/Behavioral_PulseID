package com.pulseid.behavioralpulseid;

import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import weka.classifiers.Evaluation;
import weka.classifiers.misc.IsolationForest;
import weka.core.Attribute;
import weka.core.DenseInstance;
import weka.core.Instances;
import weka.core.converters.ArffLoader;

class MlDataCollector {
    private static String[] packages = null;
    final Context context;
    public static String ARFFPATH;
    private static IsolationForest forest;
    public static String TESTPATH;
    private static Instances data;
    private static List attAppNames;
    private static List attNomHour;
    private static List attNomDay;
    SharedPreferences pref;
    SharedPreferences.Editor editor;


    public MlDataCollector(Context context) {
        this.context = context;
        ARFFPATH = context.getFilesDir().getPath().concat("/dataset.arff");
        TESTPATH = context.getFilesDir().getPath().concat("/testset.arff");
        packages = constructPackages();
        pref = context.getSharedPreferences("pulseidpreferences", 0); // 0 - for private mode
        editor = pref.edit();
    }

    private String[] constructPackages() {
        String[] pcks = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            UsageStatsManager mUsageStatsManager = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
            List<UsageStats> stats = mUsageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_BEST, 0, System.currentTimeMillis());
            pcks = new String[stats.size()];
            int e = 0;
            for (UsageStats model : stats) {
                pcks[e] = model.getPackageName();
                e++;
            }
        }
        return pcks;
    }

    public Instances createArffStruct() {
        ArrayList<Attribute> atts;
        int i;
        // 1. set up attributes
        atts = new ArrayList<>();
        // - numeric
        atts.add(new Attribute("brightness"));
        atts.add(new Attribute("orientation"));
        atts.add(new Attribute("light"));
        atts.add(new Attribute("pressure"));
        atts.add(new Attribute("ambient temperature"));
        atts.add(new Attribute("humidity"));
        atts.add(new Attribute("memmory usage"));
        atts.add(new Attribute("network statistics Tx"));
        atts.add(new Attribute("network statistics Rx"));
        atts.add(new Attribute("bluetooth statistics"));
        atts.add(new Attribute("unlock time"));
        atts.add(new Attribute("unlocks"));
        atts.add(new Attribute("horario"));
        atts.add(new Attribute("date"));
        atts.add(new Attribute("dia"));

        if (packages.equals(null))
            constructPackages();
        attAppNames = new ArrayList();
        for (String aPackage : packages) {
            attAppNames.add(aPackage);
        }
        atts.add(new Attribute("fromApp"));
        atts.add(new Attribute("toApp"));
        atts.add(new Attribute("lastMinuteApps"));
        atts.add(new Attribute("mostUsedLastDay"));
        atts.add(new Attribute("secondMostUsedLastDay"));

        List classAttr = new ArrayList();
        classAttr.add(Integer.toString(-1));
        classAttr.add(Integer.toString(1));
        atts.add(new Attribute("itIs", classAttr));

        // 2. create Instances object
        data = new Instances("senseID behaviour", atts, 0);
        return data;
    }

    public double[] collectData(float brighness, int orientation, float[] sensors, double[] memmory, long[] networkStats, long bluetoothStats,
                                long lockTime, long unlocks, String[] pausedToResumed, int appsLastMinute,
                                String[] mostUsedLastDay) {

        // 3. fill with data
        // first instance
        double[] vals = new double[21];
        // - numeric
        vals[0] = brighness;
        vals[1] = orientation;
        vals[2] = sensors[0];
        vals[3] = sensors[1];
        vals[4] = sensors[2];
        vals[5] = sensors[3];
        vals[6] = memmory[0];
        vals[7] = networkStats[0];
        vals[8] = networkStats[1];
        vals[9] = bluetoothStats;
        vals[10] = lockTime;
        vals[11] = unlocks;
        // - nominal
        Calendar rightNow = Calendar.getInstance();
        int currentHour = rightNow.get(Calendar.HOUR_OF_DAY);

        attNomHour = new ArrayList(6);
        for (int i = 0; i < 6; i++)
            attNomHour.add("temporal zone " + (i + 1));
        if (currentHour >= 0 && currentHour < 4)
            vals[12] = attNomHour.indexOf("temporal zone 1");
        else if (currentHour >= 4 && currentHour < 8)
            vals[12] = attNomHour.indexOf("temporal zone 2");
        else if (currentHour >= 8 && currentHour < 12)
            vals[12] = attNomHour.indexOf("temporal zone 3");
        else if (currentHour >= 12 && currentHour < 16)
            vals[12] = attNomHour.indexOf("temporal zone 4");
        else if (currentHour >= 16 && currentHour < 20)
            vals[12] = attNomHour.indexOf("temporal zone 5");
        else if (currentHour >= 20 && currentHour < 24)
            vals[12] = attNomHour.indexOf("temporal zone 6");
        // - date (only the day of month)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vals[13] = rightNow.get(Calendar.DAY_OF_MONTH);
        }
        // - day of week
        attNomDay = new ArrayList(7);
        attNomDay.add("Monday");
        attNomDay.add("Tuesday");
        attNomDay.add("Wednesday");
        attNomDay.add("Thursday");
        attNomDay.add("Friday");
        attNomDay.add("Saturday");
        attNomDay.add("Sunday");
        vals[14] = attNomDay.indexOf(new SimpleDateFormat("EEEE", Locale.ENGLISH).format(rightNow.getTime().getTime()));

        //Below lines are created to ensure that attAppNames array is not null
        if (attAppNames==null){
            packages = constructPackages();
            attAppNames = new ArrayList();
            for (String aPackage : packages) {
                attAppNames.add(aPackage);
            }
        }
        if (attAppNames.indexOf(pausedToResumed[0]) == (-1) || attAppNames.indexOf(pausedToResumed[1]) == (-1) || attAppNames.indexOf(mostUsedLastDay[0]) == (-1) || attAppNames.indexOf(mostUsedLastDay[1]) == (-1)) {
            packages = constructPackages();
            attAppNames = new ArrayList();
            for (String aPackage : packages) {
                attAppNames.add(aPackage);
            }
        }

        if (pausedToResumed[0] == "") {
            pausedToResumed[0] = BackgroundService.lastAppInForeground;
        }
        if (pausedToResumed[1] == "") {
            pausedToResumed[1] = BackgroundService.lastAppInForeground;
        }
        vals[15] = attAppNames.indexOf(pausedToResumed[0]);
        vals[16] = attAppNames.indexOf(pausedToResumed[1]);
        vals[17] = appsLastMinute;
        vals[18] = attAppNames.indexOf(mostUsedLastDay[0]);
        vals[19] = attAppNames.indexOf(mostUsedLastDay[1]);
        vals[20] = 1;// is supposed to be always 1 (this will be the predicted value)
        // add
        editor.putString("params_text","Current collected parameters (last 1m):\n\n" +
                " - Brighness: "+brighness+"\n" +
                " - Sensors: "+sensors[0]+", "+sensors[1]+", "+sensors[2]+", "+sensors[3]+"\n" +
                " - Memory: "+memmory[0]+" kB"+"("+(int)memmory[1]+"%)\n" +
                " - NetworkStats: "+networkStats[0]+" MB(Tx), "+networkStats[1]+" MB(Rx)\n" +
                " - BluetoothStats: "+bluetoothStats+" MB\n" +
                " - LockedTime: "+lockTime+" s\n" +
                " - Unlocks: "+unlocks+"\n" +
                " - First app: " + pausedToResumed[0] +"\n"+
                " - Last app: " + pausedToResumed[1] +"\n" +
                " - Number of apps: "+appsLastMinute+"\n" +
                " - Most used last day: "+mostUsedLastDay[0]+" & "+mostUsedLastDay[1]);
        editor.commit();
        //MainActivity.paramsView.setText(pref.getString("params_text",null));

        return vals;
    }

    public void writeArff(Instances data, String path) throws IOException {
        //WRITE THE COLLECTED INFORMATION INTO THE ARFF FILE
        FileWriter file = new FileWriter(path);
        BufferedWriter writer = new BufferedWriter(file);
        String s = data.toString();
        writer.write(s);
        writer.flush();
        writer.close();
    }

    public void appendData(float brighness, int orientation, float[] sensors, double[] memmory, long[] networkStats,
                           long bluetoothStats, long lockTime, long unlocks,
                           String[] pausedToResumed, int appsLastMinute, String[] mostUsedLastDay) throws IOException {
        Instances arff = readArff(ARFFPATH);
        double[] vals = collectData(brighness, orientation, sensors, memmory, networkStats, bluetoothStats, lockTime, unlocks, pausedToResumed, appsLastMinute,
                mostUsedLastDay);
        arff.add(new DenseInstance(1.0, vals));
        writeArff(arff, ARFFPATH);
    }

    public Instances readArff(String path) throws IOException {
        //READ FROM THE ARFF FILE THAT CONTAINS THE ARFF INFORMATION
        BufferedReader reader = new BufferedReader(new FileReader(path));
        ArffLoader.ArffReader arff = new ArffLoader.ArffReader(reader);
        Instances instances = arff.getData();
        reader.close();
        return instances;
    }

    public void train(float brighness, int orientation, float[] sensors, double[] memmory, long[] networkStats, long bluetoothStats,
                      long lockTime, long unlocks, String[] pausedToResumed, int appsLastMinute, String[] mostUsedLastDay) throws Exception {

        if (!new File(MlDataCollector.ARFFPATH).exists()) {
            Instances data = createArffStruct();
            double[] vals = collectData(brighness, orientation, sensors, memmory, networkStats, bluetoothStats, lockTime, unlocks, pausedToResumed, appsLastMinute, mostUsedLastDay);
            data.add(new DenseInstance(1.0, vals));
            writeArff(data, MlDataCollector.ARFFPATH);
        } else {
            createArffStruct();
            appendData(brighness, orientation, sensors, memmory, networkStats, bluetoothStats, lockTime, unlocks, pausedToResumed, appsLastMinute, mostUsedLastDay);
        }
        editor.putString("debug_text",new SimpleDateFormat("dd MMM yyyy HH:mm").format(new Date(System.currentTimeMillis()))+" New train instance added."+"\n"+pref.getString("debug_text",null));
        editor.commit();
        //MainActivity.debugView.setText(pref.getString("debug_text",null));
    }

    public double test(float brighness, int orientation, float[] sensors, double[] memmory, long[] networkStats,
                       long bluetoothStats, long lockTime, long unlocks,
                       String[] pausedToResumed, int appsLastMinute, String[] mostUsedLastDay) throws Exception {
        // Loading arff dataset
        Instances trainingDataSet = readArff(ARFFPATH);
        trainingDataSet.setClassIndex(trainingDataSet.numAttributes() - 1);
        //Train with IsolationForest
        forest = new IsolationForest();
        forest.buildClassifier(trainingDataSet);

        // Evaluate classifier
        Instances test;
        double[] testVals = collectData(brighness, orientation, sensors, memmory, networkStats, bluetoothStats, lockTime, unlocks, pausedToResumed, appsLastMinute, mostUsedLastDay);
        if (!new File(TESTPATH).exists()) { //If test instances do not exist, create the file and write last instance
            test = createArffStruct();
            test.add(new DenseInstance(1.0, testVals));
            test.setClassIndex(test.numAttributes() - 1);
            writeArff(test, TESTPATH);
        } else {    //If test file exists, reads it, removes older and adds new one
            test = readArff(TESTPATH);
            if (test.size() >= 20) {
                test.delete(0);
                for (int i = 0; i < (test.size() - 1); i++) {
                    test.set(i, test.instance(i + 1));
                }
            }
            test.add(new DenseInstance(1.0, testVals));
            test.setClassIndex(test.numAttributes() - 1);
            writeArff(test, TESTPATH);
        }

        Evaluation eval = new Evaluation(trainingDataSet);
        eval.evaluateModel(forest, test);

        /*System.out.println(
                eval.toSummaryString("\nResults\n======\n", false)
                        + "\n" + eval.toMatrixString()
        );*/
        editor.putString("debug_text",new SimpleDateFormat("dd MMM yyyy HH:mm").format(new Date(System.currentTimeMillis()))+" Evaluation successful."+"\n"+pref.getString("debug_text",null));
        editor.commit();
        //MainActivity.debugView.setText(pref.getString("debug_text",null));

        return eval.pctCorrect();
    }

    public double[] evaluate(Evaluation eval, Instances trainingDataset) {
        double sensitivity = (eval.numTruePositives(trainingDataset.numAttributes() - 1) / eval.numTruePositives(trainingDataset.numAttributes() - 1)) + eval.numFalseNegatives(trainingDataset.numAttributes() - 1);
        double specificity = (eval.numTrueNegatives(trainingDataset.numAttributes() - 1) / eval.numTrueNegatives(trainingDataset.numAttributes() - 1)) + eval.numFalsePositives(trainingDataset.numAttributes() - 1);
        double accuracy = (eval.numTruePositives(trainingDataset.numAttributes() - 1) + eval.numTrueNegatives(trainingDataset.numAttributes() - 1)) / (eval.numTruePositives(trainingDataset.numAttributes() - 1) + eval.numFalsePositives(trainingDataset.numAttributes() - 1) + eval.numTrueNegatives(trainingDataset.numAttributes() - 1) + eval.numFalseNegatives(trainingDataset.numAttributes() - 1));

        return new double[]{sensitivity, specificity, accuracy};
    }
}
